package com.example.VisualizationSystem.generator;

import com.example.VisualizationSystem.config.PipelineProperties;
import com.example.VisualizationSystem.dto.TransactionEdgeData;
import com.example.VisualizationSystem.model.Transaction;
import com.example.VisualizationSystem.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.summary.ResultSummary;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class Neo4jIngestionService {

    private final Driver driver;
    private final PipelineProperties props;

    // ================================================================
    //  PHASE 0 — CLEAN
    // ================================================================

    public void cleanDatabase() {
        log.info("▸ Wiping database...");
        try (Session session = driver.session()) {
            // Batch-delete to avoid heap overflow on large DBs
            int deleted;
            do {
                deleted = session.executeWrite(tx -> {
                    var result = tx.run("""
                                MATCH (n)
                                WITH n LIMIT 10000
                                DETACH DELETE n
                                RETURN count(*) AS cnt
                            """);
                    return result.single().get("cnt").asInt();
                });
                if (deleted > 0) {
                    log.info("    deleted {} nodes...", deleted);
                }
            } while (deleted > 0);
        }
        log.info("  ✓ Database cleaned");
    }

    // ================================================================
    //  PHASE 1 — SCHEMA (indexes + constraints)
    // ================================================================

    public void createSchema() {
        log.info("▸ Creating indexes and constraints...");
        try (Session session = driver.session()) {
            List<String> statements = List.of(
                    // Unique constraints (implicitly create indexes)
                    "CREATE CONSTRAINT user_id_unique IF NOT EXISTS FOR (u:User) REQUIRE u.userId IS UNIQUE",
                    "CREATE CONSTRAINT txn_id_unique IF NOT EXISTS FOR (t:Transaction) REQUIRE t.transactionId IS UNIQUE",

                    // Lookup indexes on shared attributes
                    "CREATE INDEX user_email_idx IF NOT EXISTS FOR (u:User) ON (u.email)",
                    "CREATE INDEX user_phone_idx IF NOT EXISTS FOR (u:User) ON (u.phone)",
                    "CREATE INDEX user_address_idx IF NOT EXISTS FOR (u:User) ON (u.address)",
                    "CREATE INDEX user_payment_idx IF NOT EXISTS FOR (u:User) ON (u.paymentMethod)",
                    "CREATE INDEX txn_ip_idx IF NOT EXISTS FOR (t:Transaction) ON (t.ip)",
                    "CREATE INDEX txn_device_idx IF NOT EXISTS FOR (t:Transaction) ON (t.deviceId)",
                    "CREATE INDEX txn_timestamp_idx IF NOT EXISTS FOR (t:Transaction) ON (t.timestamp)"
            );

            for (String stmt : statements) {
                try {
                    session.run(stmt).consume();
                } catch (Exception e) {
                    if (!e.getMessage().contains("already exists") &&
                            !e.getMessage().contains("equivalent")) {
                        throw e;
                    }
                }
            }
        }
        log.info("  ✓ Schema ready");
    }

    // ================================================================
    //  PHASE 3a — INSERT USER NODES
    // ================================================================

    public void insertUsers(List<User> users) {
        log.info("▸ Inserting {} User nodes (batch={})...",
                users.size(), props.getNodeBatchSize());

        /*
         * UNWIND approach: one Cypher statement processes an entire batch.
         * LocalDateTime is serialized as ISO string — Neo4j driver
         * handles java.time.LocalDateTime natively.
         */
        String cypher = """
                    UNWIND $batch AS row
                    CREATE (u:User {
                        userId:        row.userId,
                        name:          row.name,
                        email:         row.email,
                        phone:         row.phone,
                        address:       row.address,
                        paymentMethod: row.paymentMethod,
                        createdAt:     row.createdAt
                    })
                """;

        List<Map<String, Object>> maps = users.stream()
                .map(this::userToMap)
                .collect(Collectors.toList());

        batchWrite(cypher, maps, props.getNodeBatchSize());
        log.info("  ✓ {} Users inserted", users.size());
    }

    // ================================================================
    //  PHASE 3b — INSERT TRANSACTION NODES
    // ================================================================

    public void insertTransactions(List<Transaction> txns) {
        log.info("▸ Inserting {} Transaction nodes (batch={})...",
                txns.size(), props.getNodeBatchSize());

        String cypher = """
                    UNWIND $batch AS row
                    CREATE (t:Transaction {
                        transactionId: row.transactionId,
                        amount:        row.amount,
                        timestamp:     row.timestamp,
                        ip:            row.ip,
                        deviceId:      row.deviceId
                    })
                """;

        List<Map<String, Object>> maps = txns.stream()
                .map(this::txnToMap)
                .collect(Collectors.toList());

        batchWrite(cypher, maps, props.getNodeBatchSize());
        log.info("  ✓ {} Transactions inserted", txns.size());
    }

    // ================================================================
    //  PHASE 4a — SENT / RECEIVED_BY edges
    // ================================================================

    public void createParticipationEdges(List<TransactionEdgeData> edges) {

        List<Map<String, Object>> edgeMaps = edges.stream()
                .map(e -> Map.<String, Object>of(
                        "txnId", e.getTransaction().getTransactionId(),
                        "senderId", e.getSenderId(),
                        "receiverId", e.getReceiverId(),
                        "amount", e.getTransaction().getAmount()
                ))
                .collect(Collectors.toList());

        // ── SENT ───────────────────────────────────────────────
        log.info("▸ Creating SENT edges...");
        String sentCypher = """
                    UNWIND $batch AS row
                    MATCH (u:User {userId: row.senderId})
                    MATCH (t:Transaction {transactionId: row.txnId})
                    CREATE (u)-[:SENT {amount: row.amount}]->(t)
                """;
        batchWrite(sentCypher, edgeMaps, props.getRelationshipBatchSize());
        log.info("  ✓ SENT edges created");

        // ── RECEIVED_BY ────────────────────────────────────────
        log.info("▸ Creating RECEIVED_BY edges...");
        String recvCypher = """
                    UNWIND $batch AS row
                    MATCH (t:Transaction {transactionId: row.txnId})
                    MATCH (u:User {userId: row.receiverId})
                    CREATE (t)-[:RECEIVED_BY {amount: row.amount}]->(u)
                """;
        batchWrite(recvCypher, edgeMaps, props.getRelationshipBatchSize());
        log.info("  ✓ RECEIVED_BY edges created");
    }

    // ================================================================
    //  PHASE 4b — TRANSFERRED_TO (aggregated per user pair)
    // ================================================================

    public void createTransferEdges(List<TransactionEdgeData> edges) {
        log.info("▸ Creating TRANSFERRED_TO edges...");

        // Aggregate in Java: one edge per (sender→receiver) pair
        Map<String, Map<String, Object>> pairs = new LinkedHashMap<>();
        for (TransactionEdgeData e : edges) {
            String key = e.getSenderId() + "|" + e.getReceiverId();
            pairs.computeIfAbsent(key, k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("senderId", e.getSenderId());
                m.put("receiverId", e.getReceiverId());
                m.put("totalAmount", 0.0);
                m.put("txnCount", 0L);
                return m;
            });
            Map<String, Object> m = pairs.get(key);
            m.put("totalAmount", (double) m.get("totalAmount") + e.getTransaction().getAmount());
            m.put("txnCount", (long) m.get("txnCount") + 1);
        }

        String cypher = """
                    UNWIND $batch AS row
                    MATCH (s:User {userId: row.senderId})
                    MATCH (r:User {userId: row.receiverId})
                    CREATE (s)-[:TRANSFERRED_TO {
                        totalAmount: row.totalAmount,
                        txnCount:    row.txnCount
                    }]->(r)
                """;

        batchWrite(cypher, new ArrayList<>(pairs.values()),
                props.getRelationshipBatchSize());
        log.info("  ✓ {} TRANSFERRED_TO edges created", pairs.size());
    }

    // ================================================================
    //  PHASE 5a — SHARED USER ATTRIBUTE EDGES
    // ================================================================

    public void createSharedUserAttributeEdges() {
        linkUsersByAttribute("email", "SAME_EMAIL");
        linkUsersByAttribute("phone", "SAME_PHONE");
        linkUsersByAttribute("address", "SAME_ADDRESS");
        linkUsersByAttribute("paymentMethod", "SAME_PAYMENT_METHOD");
    }

    /**
     * For each distinct attribute value:
     * - small cluster (≤ maxPairwise): create pairwise edges
     * - large cluster (> maxPairwise): create hub + spoke edges
     * <p>
     * The range(i, j) idiom ensures each pair is created exactly once.
     */
    private void linkUsersByAttribute(String attr, String relType) {
        log.info("▸ Detecting {} relationships (fully pairwise)...", relType);

        String pairwiseCypher = String.format("""
                    MATCH (u:User)
                    WHERE u.%1$s IS NOT NULL
                    WITH u.%1$s AS val, collect(u) AS nodes
                    WHERE size(nodes) > 1
                    UNWIND range(0, size(nodes)-2) AS i
                    UNWIND range(i+1, size(nodes)-1) AS j
                    WITH nodes[i] AS a, nodes[j] AS b
                    CREATE (a)-[:%2$s]->(b)
                """, attr, relType);

        try (Session s = driver.session()) {
            var rs = s.run(pairwiseCypher).consume();
            log.info("  ✓ {} edges created for {}", rs.counters().relationshipsCreated(), relType);
        }
    }


    // ================================================================
    //  PHASE 5b — SHARED TRANSACTION ATTRIBUTE EDGES
    // ================================================================

    public void createSharedTransactionAttributeEdges() {
        linkTransactionsByAttribute("ip", "SAME_IP");
        linkTransactionsByAttribute("deviceId", "SAME_DEVICE");
    }

    /**
     * 100k transactions can produce huge groups.
     * Strategy: fetch distinct values → batch 200 at a time → targeted UNWIND.
     */
    private void linkTransactionsByAttribute(String attr, String relType) {
        log.info("▸ Detecting {} relationships (fully pairwise)...", relType);

        String pairwiseCypher = String.format("""
                    MATCH (t:Transaction)
                    WHERE t.%1$s IS NOT NULL
                    WITH t.%1$s AS val, collect(t) AS nodes
                    WHERE size(nodes) > 1
                    UNWIND range(0, size(nodes)-2) AS i
                    UNWIND range(i+1, size(nodes)-1) AS j
                    WITH nodes[i] AS a, nodes[j] AS b
                    CREATE (a)-[:%2$s]->(b)
                """, attr, relType);

        try (Session s = driver.session()) {
            var rs = s.run(pairwiseCypher).consume();
            log.info("  ✓ {} edges created for {}", rs.counters().relationshipsCreated(), relType);
        }
    }


    // ================================================================
    //  INTERNAL HELPERS
    // ================================================================

    /**
     * Partition data → one UNWIND write-transaction per chunk.
     */
    private void batchWrite(String cypher,
                            List<Map<String, Object>> data,
                            int batchSize) {
        try (Session session = driver.session()) {
            for (int i = 0; i < data.size(); i += batchSize) {
                int end = Math.min(i + batchSize, data.size());
                List<Map<String, Object>> chunk = data.subList(i, end);

                session.executeWrite(tx -> {
                    tx.run(cypher, Map.of("batch", chunk)).consume();
                    return null;
                });

                // Progress logging every 5 batches
                int batchNum = i / batchSize;
                if (batchNum % 5 == 0) {
                    log.info("    batch {}/{}", end, data.size());
                }
            }
        }
    }

    private Map<String, Object> userToMap(User u) {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", u.getUserId());
        map.put("name", u.getName());
        map.put("email", u.getEmail());
        map.put("phone", u.getPhone());
        map.put("address", u.getAddress());
        map.put("paymentMethod", u.getPaymentMethod());
        map.put("createdAt", u.getCreatedAt());   // Driver handles LocalDateTime
        return map;
    }

    private Map<String, Object> txnToMap(Transaction t) {
        Map<String, Object> map = new HashMap<>();
        map.put("transactionId", t.getTransactionId());
        map.put("amount", t.getAmount());
        map.put("timestamp", t.getTimestamp());    // Driver handles LocalDateTime
        map.put("ip", t.getIp());
        map.put("deviceId", t.getDeviceId());
        return map;
    }
}