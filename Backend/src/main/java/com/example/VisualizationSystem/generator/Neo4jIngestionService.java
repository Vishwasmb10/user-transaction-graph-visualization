package com.example.VisualizationSystem.generator;

import com.example.VisualizationSystem.config.PipelineProperties;
import com.example.VisualizationSystem.dto.TransactionEdgeData;
import com.example.VisualizationSystem.model.Transaction;
import com.example.VisualizationSystem.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class Neo4jIngestionService {

    private final Driver driver;
    private final PipelineProperties props;

    public static final List<String> PAYMENT_METHOD_TYPES = List.of(
            "CREDIT_CARD", "DEBIT_CARD", "CASH",
            "BANK_TRANSFER", "UPI", "PAYPAL", "CRYPTO"
    );

    // ════════════════════════════════════════════════════════════════
    //  PHASE 0 — CLEAN DATABASE
    // ════════════════════════════════════════════════════════════════

    public void cleanDatabase() {
        log.info("▸ Wiping database...");
        try (Session session = driver.session()) {
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
                if (deleted > 0) log.info("    deleted {} nodes...", deleted);
            } while (deleted > 0);
        }
        log.info("  ✓ Database cleaned");
    }

    // ════════════════════════════════════════════════════════════════
    //  PHASE 1 — CREATE SCHEMA
    // ════════════════════════════════════════════════════════════════

    public void createSchema() {
        log.info("▸ Creating indexes and constraints...");
        try (Session session = driver.session()) {
            List<String> statements = List.of(
                    "CREATE CONSTRAINT user_id_unique  IF NOT EXISTS FOR (u:User)          REQUIRE u.userId IS UNIQUE",
                    "CREATE CONSTRAINT txn_id_unique   IF NOT EXISTS FOR (t:Transaction)   REQUIRE t.transactionId IS UNIQUE",
                    "CREATE CONSTRAINT pm_name_unique  IF NOT EXISTS FOR (p:PaymentMethod) REQUIRE p.name IS UNIQUE",

                    "CREATE INDEX user_email_idx       IF NOT EXISTS FOR (u:User)          ON (u.email)",
                    "CREATE INDEX user_phone_idx       IF NOT EXISTS FOR (u:User)          ON (u.phone)",
                    "CREATE INDEX user_address_idx     IF NOT EXISTS FOR (u:User)          ON (u.address)",

                    "CREATE INDEX txn_ip_idx           IF NOT EXISTS FOR (t:Transaction)   ON (t.ip)",
                    "CREATE INDEX txn_device_idx       IF NOT EXISTS FOR (t:Transaction)   ON (t.deviceId)",
                    "CREATE INDEX txn_timestamp_idx    IF NOT EXISTS FOR (t:Transaction)   ON (t.timestamp)",
                    "CREATE INDEX txn_status_idx       IF NOT EXISTS FOR (t:Transaction)   ON (t.status)",
                    "CREATE INDEX txn_pm_idx           IF NOT EXISTS FOR (t:Transaction)   ON (t.paymentMethod)",
                    "CREATE INDEX txn_currency_idx     IF NOT EXISTS FOR (t:Transaction)   ON (t.currency)"
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

    // ════════════════════════════════════════════════════════════════
    //  PHASE 2 — CREATE PAYMENT METHOD NODES
    // ════════════════════════════════════════════════════════════════

    public void createPaymentMethodNodes() {
        log.info("▸ Creating PaymentMethod hub nodes...");
        try (Session s = driver.session()) {
            s.executeWrite(tx -> {
                tx.run("""
                        UNWIND $methods AS name
                        MERGE (p:PaymentMethod {name: name})
                        """, Map.of("methods", PAYMENT_METHOD_TYPES)).consume();
                return null;
            });
        }
        log.info("  ✓ {} PaymentMethod nodes created", PAYMENT_METHOD_TYPES.size());
    }

    // ════════════════════════════════════════════════════════════════
    //  PHASE 3a — INSERT USER NODES
    // ════════════════════════════════════════════════════════════════

    public void insertUsers(List<User> users) {
        log.info("▸ Inserting {} User nodes (batch={})...",
                users.size(), props.getNodeBatchSize());

        String cypher = """
                UNWIND $batch AS row
                CREATE (u:User {
                    userId:         row.userId,
                    name:           row.name,
                    email:          row.email,
                    phone:          row.phone,
                    address:        row.address,
                    paymentMethods: row.paymentMethods,
                    createdAt:      row.createdAt
                })
                """;

        List<Map<String, Object>> maps = users.stream()
                .map(this::userToMap)
                .collect(Collectors.toList());

        batchWrite(cypher, maps, props.getNodeBatchSize());
        log.info("  ✓ {} Users inserted", users.size());
    }

    // ════════════════════════════════════════════════════════════════
    //  PHASE 3b — INSERT TRANSACTION NODES
    // ════════════════════════════════════════════════════════════════

    public void insertTransactions(List<Transaction> txns) {
        log.info("▸ Inserting {} Transaction nodes (batch={})...",
                txns.size(), props.getNodeBatchSize());

        String cypher = """
                UNWIND $batch AS row
                CREATE (t:Transaction {
                    transactionId:  row.transactionId,
                    amount:         row.amount,
                    currency:       row.currency,
                    timestamp:      row.timestamp,
                    ip:             row.ip,
                    deviceId:       row.deviceId,
                    status:         row.status,
                    paymentMethod:  row.paymentMethod
                })
                """;

        List<Map<String, Object>> maps = txns.stream()
                .map(this::txnToMap)
                .collect(Collectors.toList());

        batchWrite(cypher, maps, props.getNodeBatchSize());
        log.info("  ✓ {} Transactions inserted", txns.size());
    }

    // ════════════════════════════════════════════════════════════════
    //  PHASE 4a — CREATE SENT / RECEIVED_BY EDGES
    // ════════════════════════════════════════════════════════════════

    public void createParticipationEdges(List<TransactionEdgeData> edges) {
        List<Map<String, Object>> edgeMaps = edges.stream()
                .map(e -> Map.<String, Object>of(
                        "txnId", e.getTransaction().getTransactionId(),
                        "senderId", e.getSenderId(),
                        "receiverId", e.getReceiverId(),
                        "amount", e.getTransaction().getAmount()
                ))
                .collect(Collectors.toList());

        log.info("▸ Creating SENT edges...");
        batchWrite("""
                UNWIND $batch AS row
                MATCH (u:User {userId: row.senderId})
                MATCH (t:Transaction {transactionId: row.txnId})
                CREATE (u)-[:SENT {amount: row.amount}]->(t)
                """, edgeMaps, props.getRelationshipBatchSize());
        log.info("  ✓ {} SENT edges created", edgeMaps.size());

        log.info("▸ Creating RECEIVED_BY edges...");
        batchWrite("""
                UNWIND $batch AS row
                MATCH (t:Transaction {transactionId: row.txnId})
                MATCH (u:User {userId: row.receiverId})
                CREATE (t)-[:RECEIVED_BY {amount: row.amount}]->(u)
                """, edgeMaps, props.getRelationshipBatchSize());
        log.info("  ✓ {} RECEIVED_BY edges created", edgeMaps.size());
    }

    // ════════════════════════════════════════════════════════════════
    //  PHASE 4b — CREATE TRANSFERRED_TO EDGES
    // ════════════════════════════════════════════════════════════════

    public void createTransferEdges(List<TransactionEdgeData> edges) {
        if (!props.isCreateTransferEdges()) {
            log.info("▸ Skipping TRANSFERRED_TO edges (disabled in config)");
            return;
        }

        log.info("▸ Creating TRANSFERRED_TO edges...");

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

        batchWrite("""
                UNWIND $batch AS row
                MATCH (s:User {userId: row.senderId})
                MATCH (r:User {userId: row.receiverId})
                CREATE (s)-[:TRANSFERRED_TO {
                    totalAmount: row.totalAmount,
                    txnCount:    row.txnCount
                }]->(r)
                """, new ArrayList<>(pairs.values()), props.getRelationshipBatchSize());
        log.info("  ✓ {} TRANSFERRED_TO edges created", pairs.size());
    }

    // ════════════════════════════════════════════════════════════════
    //  PHASE 5a — SHARED USER ATTRIBUTE EDGES (OPTIMIZED)
    // ════════════════════════════════════════════════════════════════

    public void createSharedUserAttributeEdges() {
        if (props.isCreateSameEmail()) {
            linkUsersByAttribute("email", "SAME_EMAIL",
                    props.getSameEmailSampleRate(),
                    props.getMaxEmailCluster());
        } else {
            log.info("▸ Skipping SAME_EMAIL edges (disabled)");
        }

        if (props.isCreateSamePhone()) {
            linkUsersByAttribute("phone", "SAME_PHONE",
                    props.getSamePhoneSampleRate(),
                    props.getMaxPhoneCluster());
        } else {
            log.info("▸ Skipping SAME_PHONE edges (disabled)");
        }

        if (props.isCreateSameAddress()) {
            linkUsersByAttribute("address", "SAME_ADDRESS",
                    props.getSameAddressSampleRate(),
                    props.getMaxAddressCluster());
        } else {
            log.info("▸ Skipping SAME_ADDRESS edges (disabled)");
        }

        if (props.isCreateUsesPayment()) {
            linkUsersToPaymentMethodHubs();
        } else {
            log.info("▸ Skipping USES_PAYMENT edges (disabled)");
        }
    }

    private void linkUsersByAttribute(String attr, String relType, double sampleRate, int maxCluster) {
        log.info("▸ Creating {} edges (sample={}%, maxCluster={})...",
                relType, String.format("%.1f", sampleRate * 100), maxCluster);

        String cypher = String.format("""
                MATCH (u:User)
                WHERE u.%1$s IS NOT NULL
                WITH u.%1$s AS val, collect(u) AS nodes
                WHERE size(nodes) > 1 AND size(nodes) <= %3$d
                UNWIND range(0, size(nodes)-2) AS i
                UNWIND range(i+1, size(nodes)-1) AS j
                WITH nodes[i] AS a, nodes[j] AS b
                WHERE rand() < %4$f
                CREATE (a)-[:%2$s]->(b)
                """, attr, relType, maxCluster, sampleRate);

        try (Session s = driver.session()) {
            var rs = s.run(cypher).consume();
            log.info("  ✓ {} edges created for {}", rs.counters().relationshipsCreated(), relType);
        }
    }

    private void linkUsersToPaymentMethodHubs() {
        log.info("▸ Creating USES_PAYMENT edges...");

        String cypher = """
                MATCH (u:User)
                WHERE u.paymentMethods IS NOT NULL AND size(u.paymentMethods) > 0
                UNWIND u.paymentMethods AS pm
                MATCH (p:PaymentMethod {name: pm})
                CREATE (u)-[:USES_PAYMENT]->(p)
                """;

        try (Session s = driver.session()) {
            var rs = s.run(cypher).consume();
            log.info("  ✓ {} USES_PAYMENT edges created", rs.counters().relationshipsCreated());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  PHASE 5b — SHARED TRANSACTION ATTRIBUTE EDGES (OPTIMIZED)
    // ════════════════════════════════════════════════════════════════

    public void createSharedTransactionAttributeEdges() {
        if (props.isCreateSameIp()) {
            linkTransactionsByAttribute("ip", "SAME_IP",
                    props.getSameIpSampleRate(),
                    props.getMaxIpCluster());
        } else {
            log.info("▸ Skipping SAME_IP edges (disabled)");
        }

        if (props.isCreateSameDevice()) {
            linkTransactionsByAttribute("deviceId", "SAME_DEVICE",
                    props.getSameDeviceSampleRate(),
                    props.getMaxDeviceCluster());
        } else {
            log.info("▸ Skipping SAME_DEVICE edges (disabled)");
        }
    }

    private void linkTransactionsByAttribute(String attr, String relType, double sampleRate, int maxCluster) {
        log.info("▸ Creating {} edges (sample={}%, maxCluster={})...",
                relType, String.format("%.1f", sampleRate * 100), maxCluster);

        String cypher = String.format("""
                MATCH (t:Transaction)
                WHERE t.%1$s IS NOT NULL
                WITH t.%1$s AS val, collect(t) AS nodes
                WHERE size(nodes) > 1 AND size(nodes) <= %3$d
                UNWIND range(0, size(nodes)-2) AS i
                UNWIND range(i+1, size(nodes)-1) AS j
                WITH nodes[i] AS a, nodes[j] AS b
                WHERE rand() < %4$f
                CREATE (a)-[:%2$s]->(b)
                """, attr, relType, maxCluster, sampleRate);

        try (Session s = driver.session()) {
            var rs = s.run(cypher).consume();
            log.info("  ✓ {} edges created for {}", rs.counters().relationshipsCreated(), relType);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  STATISTICS
    // ════════════════════════════════════════════════════════════════

    public void logDatabaseStats() {
        log.info("═══════════════════════════════════════════════════════════");
        log.info("                   DATABASE STATISTICS                      ");
        log.info("═══════════════════════════════════════════════════════════");

        try (Session session = driver.session()) {
            // Node counts
            var nodeResult = session.run("""
                    MATCH (n)
                    WITH labels(n) AS lbls, count(*) AS cnt
                    UNWIND lbls AS label
                    RETURN label, sum(cnt) AS count
                    ORDER BY count DESC
                    """);

            log.info("NODES:");
            long totalNodes = 0;
            while (nodeResult.hasNext()) {
                var record = nodeResult.next();
                long count = record.get("count").asLong();
                totalNodes += count;
                log.info("  {:20} {:>10,}", record.get("label").asString(), count);
            }
            log.info("  {:20} {:>10,}", "TOTAL", totalNodes);

            // Relationship counts
            var relResult = session.run("""
                    MATCH ()-[r]->()
                    RETURN type(r) AS type, count(r) AS count
                    ORDER BY count DESC
                    """);

            log.info("\nRELATIONSHIPS:");
            long totalRels = 0;
            while (relResult.hasNext()) {
                var record = relResult.next();
                long count = record.get("count").asLong();
                totalRels += count;
                log.info("  {:20} {:>10,}", record.get("type").asString(), count);
            }
            log.info("  {:20} {:>10,}", "TOTAL", totalRels);

            // Check against limit
            long limit = 400_000;
            log.info("\nAURA FREE TIER STATUS:");
            if (totalRels > limit) {
                log.warn("  ⚠️  OVER LIMIT! {}/{} ({:.1f}%)",
                        totalRels, limit, totalRels * 100.0 / limit);
            } else {
                log.info("  ✅ Within limit: {}/{} ({:.1f}%) | {} remaining",
                        totalRels, limit, totalRels * 100.0 / limit, limit - totalRels);
            }
        }
        log.info("═══════════════════════════════════════════════════════════");
    }

    // ════════════════════════════════════════════════════════════════
    //  HELPER METHODS
    // ════════════════════════════════════════════════════════════════

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
        map.put("paymentMethods", u.getPaymentMethods());
        map.put("createdAt", u.getCreatedAt());
        return map;
    }

    private Map<String, Object> txnToMap(Transaction t) {
        Map<String, Object> map = new HashMap<>();
        map.put("transactionId", t.getTransactionId());
        map.put("amount", t.getAmount());
        map.put("currency", t.getCurrency());
        map.put("timestamp", t.getTimestamp());
        map.put("ip", t.getIp());
        map.put("deviceId", t.getDeviceId());
        map.put("status", t.getStatus());
        map.put("paymentMethod", t.getPaymentMethod());
        return map;
    }
}