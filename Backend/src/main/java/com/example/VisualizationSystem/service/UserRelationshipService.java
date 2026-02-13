package com.example.VisualizationSystem.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserRelationshipService {

    private final Neo4jClient neo4jClient;

    private static final int MAX_PAYMENT_PEER_NODES = 50;

    public Map<String, Object> getUserGraph(String userId) {

        String query = """
                MATCH (u:User {userId: $userId})
                WITH u,
                     [(u)-[r:SAME_EMAIL|SAME_PHONE|SAME_ADDRESS]-(other:User) |
                        {
                          userId: other.userId,
                          name: other.name,
                          relType: type(r)
                        }
                     ] AS connectedUsers,
                     [(u)-[:USES_PAYMENT]->(pm:PaymentMethod) | pm.name] AS userPaymentMethods

                // ── Payment peers grouped by method (for sidebar + graph nodes) ──
                CALL (u) {
                    WITH u
                    OPTIONAL MATCH (u)-[:USES_PAYMENT]->(pm:PaymentMethod)<-[:USES_PAYMENT]-(peer:User)
                    WHERE peer.userId <> u.userId
                    WITH pm.name AS method, collect(DISTINCT peer { .userId, .name }) AS peers
                    WHERE method IS NOT NULL
                    RETURN collect({
                        method:    method,
                        peerCount: size(peers),
                        peers:     peers[0..50]
                    }) AS paymentSummary
                }

                // ── Transactions with full detail ──
                CALL (u) {
                    WITH u
                    OPTIONAL MATCH (u)-[:SENT]->(tx:Transaction)-[:RECEIVED_BY]->(receiver:User)
                    RETURN collect(tx {
                        .transactionId,
                        .amount,
                        .currency,
                        .ip,
                        .deviceId,
                        .status,
                        .paymentMethod,
                        timestamp: toString(tx.timestamp)
                    }) AS transactions,
                    collect(receiver { .userId, .name }) AS receivers
                }

                RETURN u { .userId, .name, .email, .paymentMethods } AS user,
                       connectedUsers,
                       userPaymentMethods,
                       paymentSummary,
                       transactions,
                       receivers
                """;

        Map<String, Object> raw = neo4jClient.query(query)
                .bind(userId).to("userId")
                .fetch()
                .one()
                .orElse(Map.of());

        if (raw.isEmpty()) {
            return Map.of(
                    "nodes", List.of(),
                    "edges", List.of(),
                    "paymentSummary", List.of(),
                    "userPaymentMethods", List.of()
            );
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        Set<String> addedNodeIds = new HashSet<>();
        Set<String> addedEdgeKeys = new HashSet<>();

        // ─────────────────────────────────────────────
        // 1️⃣ Main User Node
        // ─────────────────────────────────────────────
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) raw.get("user");
        String mainUserId = (String) user.get("userId");

        Map<String, Object> mainUserNode = new HashMap<>();
        mainUserNode.put("id", mainUserId);
        mainUserNode.put("label", user.get("name"));
        mainUserNode.put("type", "user");
        if (user.get("email") != null) mainUserNode.put("email", user.get("email"));
        if (user.get("paymentMethods") != null) mainUserNode.put("paymentMethods", user.get("paymentMethods"));

        nodes.add(mainUserNode);
        addedNodeIds.add(mainUserId);

        // ─────────────────────────────────────────────
        // 2️⃣ Connected Users (SAME_EMAIL / PHONE / ADDRESS)
        // ─────────────────────────────────────────────
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> connectedUsers =
                (List<Map<String, Object>>) raw.get("connectedUsers");

        if (connectedUsers != null) {
            for (Map<String, Object> cu : connectedUsers) {
                String id = (String) cu.get("userId");
                if (!addedNodeIds.contains(id)) {
                    nodes.add(Map.of("id", id, "label", cu.get("name"), "type", "user"));
                    addedNodeIds.add(id);
                }
                String edgeKey = mainUserId + "|" + cu.get("relType") + "|" + id;
                if (addedEdgeKeys.add(edgeKey)) {
                    edges.add(Map.of(
                            "source", mainUserId,
                            "target", id,
                            "type", cu.get("relType")
                    ));
                }
            }
        }

        // ─────────────────────────────────────────────
        // 3️⃣ Enriched Transactions
        // ─────────────────────────────────────────────
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> transactions =
                (List<Map<String, Object>>) raw.get("transactions");

        if (transactions != null) {
            for (Map<String, Object> tx : transactions) {
                if (tx == null || tx.get("transactionId") == null) continue;
                String txId = (String) tx.get("transactionId");

                if (!addedNodeIds.contains(txId)) {
                    nodes.add(buildTxNode(tx));
                    addedNodeIds.add(txId);
                }

                edges.add(Map.of(
                        "id", mainUserId + "_" + txId + "_SENT",
                        "source", mainUserId,
                        "target", txId,
                        "type", "SENT"
                ));
            }
        }

        // ─────────────────────────────────────────────
        // 4️⃣ Receivers
        // ─────────────────────────────────────────────
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> receivers =
                (List<Map<String, Object>>) raw.get("receivers");

        if (transactions != null && receivers != null) {
            for (int i = 0; i < transactions.size(); i++) {
                Map<String, Object> txMap = transactions.get(i);
                if (txMap == null || txMap.get("transactionId") == null) continue;
                String txId = (String) txMap.get("transactionId");

                if (i < receivers.size()) {
                    Map<String, Object> receiver = receivers.get(i);
                    if (receiver == null || receiver.get("userId") == null) continue;
                    String receiverId = (String) receiver.get("userId");

                    if (!addedNodeIds.contains(receiverId)) {
                        nodes.add(Map.of("id", receiverId, "label", receiver.get("name"), "type", "user"));
                        addedNodeIds.add(receiverId);
                    }

                    edges.add(Map.of(
                            "id", txId + "_" + receiverId + "_RECEIVED_BY",
                            "source", txId,
                            "target", receiverId,
                            "type", "RECEIVED_BY"
                    ));
                }
            }
        }

        // ─────────────────────────────────────────────
        // 5️⃣ Payment Methods — sidebar data + graph nodes/edges
        // ─────────────────────────────────────────────

        // 5a. Flat list of method names this user has
        @SuppressWarnings("unchecked")
        List<String> userPaymentMethods =
                (List<String>) raw.get("userPaymentMethods");

        List<String> cleanMethods = userPaymentMethods != null
                ? userPaymentMethods.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
                : List.of();

        // 5b. Grouped summary with peers
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> paymentSummary =
                (List<Map<String, Object>>) raw.get("paymentSummary");

        List<Map<String, Object>> cleanSummary = paymentSummary != null
                ? paymentSummary.stream()
                .filter(ps -> ps != null && ps.get("method") != null)
                .collect(Collectors.toList())
                : List.of();

        // 5c. ★ NEW: Add payment peer NODES and SAME_PAYMENT edges to the graph
        //     Cap total unique payment peers at MAX_PAYMENT_PEER_NODES
        int paymentPeerCount = 0;
        for (Map<String, Object> methodEntry : cleanSummary) {
            if (paymentPeerCount >= MAX_PAYMENT_PEER_NODES) break;

            String method = (String) methodEntry.get("method");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> peers = (List<Map<String, Object>>) methodEntry.get("peers");

            if (peers == null || method == null) continue;

            for (Map<String, Object> peer : peers) {
                if (paymentPeerCount >= MAX_PAYMENT_PEER_NODES) break;

                String peerId = (String) peer.get("userId");
                String peerName = (String) peer.get("name");
                if (peerId == null) continue;

                // Add peer as a user node if not already present
                if (!addedNodeIds.contains(peerId)) {
                    Map<String, Object> peerNode = new HashMap<>();
                    peerNode.put("id", peerId);
                    peerNode.put("label", peerName != null ? peerName : peerId);
                    peerNode.put("type", "user");
                    nodes.add(peerNode);
                    addedNodeIds.add(peerId);
                    paymentPeerCount++;
                }

                // Add SAME_PAYMENT edge (deduplicated per method+peer combo)
                String edgeKey = mainUserId + "|SAME_PAYMENT|" + peerId + "|" + method;
                if (addedEdgeKeys.add(edgeKey)) {
                    Map<String, Object> edge = new HashMap<>();
                    edge.put("id", mainUserId + "_" + peerId + "_SAME_PAYMENT_" + method);
                    edge.put("source", mainUserId);
                    edge.put("target", peerId);
                    edge.put("type", "SAME_PAYMENT");
                    edge.put("method", method);  // extra metadata for tooltip
                    edges.add(edge);
                }
            }
        }

        // ─────────────────────────────────────────────
        // 6️⃣ Build Response
        // ─────────────────────────────────────────────
        Map<String, Object> result = new HashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("userPaymentMethods", cleanMethods);
        result.put("paymentSummary", cleanSummary);

        return result;
    }

    /**
     * Build an enriched transaction node map from raw Cypher result.
     */
    private Map<String, Object> buildTxNode(Map<String, Object> tx) {
        Map<String, Object> node = new HashMap<>();
        String txId = (String) tx.get("transactionId");

        node.put("id", txId);
        node.put("type", "transaction");
        node.put("transactionId", txId);

        Object amount = tx.get("amount");
        Object currency = tx.get("currency");
        String currStr = currency != null ? currency.toString() : "$";
        if (amount != null) {
            node.put("label", String.format("%s%.2f", currStr, ((Number) amount).doubleValue()));
        } else {
            node.put("label", txId);
        }

        if (amount != null)                     node.put("amount", amount);
        if (currency != null)                   node.put("currency", currency);
        if (tx.get("timestamp") != null)        node.put("timestamp", tx.get("timestamp").toString());
        if (tx.get("ip") != null)               node.put("ip", tx.get("ip"));
        if (tx.get("deviceId") != null)         node.put("deviceId", tx.get("deviceId"));
        if (tx.get("status") != null)           node.put("status", tx.get("status"));
        if (tx.get("paymentMethod") != null)    node.put("paymentMethod", tx.get("paymentMethod"));

        return node;
    }
}