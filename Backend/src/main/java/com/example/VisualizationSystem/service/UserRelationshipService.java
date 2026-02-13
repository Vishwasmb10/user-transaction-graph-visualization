package com.example.VisualizationSystem.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class UserRelationshipService {

    private final Neo4jClient neo4jClient;

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

                // ── Payment peers grouped by method (for sidebar) ──
                CALL (u) {
                    WITH u
                    OPTIONAL MATCH (u)-[:USES_PAYMENT]->(pm:PaymentMethod)<-[:USES_PAYMENT]-(peer:User)
                    WHERE peer.userId <> u.userId
                    WITH pm.name AS method, collect(DISTINCT peer { .userId, .name }) AS peers
                    RETURN collect({
                        method:    method,
                        peerCount: size(peers),
                        peers:     peers[0..10]
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

                RETURN u { .userId, .name, .email } AS user,
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
            return Map.of("nodes", List.of(), "edges", List.of(), "paymentSummary", List.of());
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

        nodes.add(Map.of("id", mainUserId, "label", user.get("name"), "type", "user"));
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
                String txId = (String) transactions.get(i).get("transactionId");
                if (i < receivers.size()) {
                    Map<String, Object> receiver = receivers.get(i);
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
        // 5️⃣ Payment Summary (sidebar data, NOT graph nodes)
        // ─────────────────────────────────────────────
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> paymentSummary =
                (List<Map<String, Object>>) raw.get("paymentSummary");

        Map<String, Object> result = new HashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("paymentSummary", paymentSummary != null ? paymentSummary : List.of());

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

        // Amount-based label for quick visual scanning
        Object amount = tx.get("amount");
        Object currency = tx.get("currency");
        String currStr = currency != null ? currency.toString() : "$";
        if (amount != null) {
            node.put("label", String.format("%s%.2f", currStr, ((Number) amount).doubleValue()));
        } else {
            node.put("label", txId);
        }

        // Pass through all metadata for frontend tooltips/detail panel
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