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

        // ✅ CHANGED: removed SAME_PAYMENT_METHOD from pattern comprehension
        //             added payment hub traversal + payment peers via CALL subquery
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

                CALL (u) {
                    WITH u
                    OPTIONAL MATCH (u)-[:USES_PAYMENT]->(pm:PaymentMethod)<-[:USES_PAYMENT]-(peer:User)
                    WHERE peer.userId <> u.userId
                    RETURN collect(DISTINCT {
                        userId: peer.userId,
                        name:   peer.name,
                        method: pm.name
                    })[0..50] AS paymentPeers
                }

                CALL (u) {
                    WITH u
                    OPTIONAL MATCH (u)-[:SENT]->(tx:Transaction)-[:RECEIVED_BY]->(receiver:User)
                    RETURN collect(tx { .transactionId, .amount }) AS transactions,
                           collect(receiver { .userId, .name }) AS receivers
                }

                RETURN u { .userId, .name, .email } AS user,
                       connectedUsers,
                       userPaymentMethods,
                       paymentPeers,
                       transactions,
                       receivers
                """;

        Map<String, Object> raw = neo4jClient.query(query)
                .bind(userId).to("userId")
                .fetch()
                .one()
                .orElse(Map.of());

        if (raw.isEmpty()) {
            return Map.of("nodes", List.of(), "edges", List.of());
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        Set<String> addedNodeIds = new HashSet<>();
        Set<String> addedEdgeKeys = new HashSet<>();   // ✅ NEW: dedup edges

        // ─────────────────────────────────────────────
        // 1️⃣ Main User Node
        // ─────────────────────────────────────────────
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) raw.get("user");
        String mainUserId = (String) user.get("userId");

        nodes.add(Map.of(
                "id", mainUserId,
                "label", user.get("name"),
                "type", "user"
        ));
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
                    nodes.add(Map.of(
                            "id", id,
                            "label", cu.get("name"),
                            "type", "user"
                    ));
                    addedNodeIds.add(id);
                }

                edges.add(Map.of(
                        "source", mainUserId,
                        "target", id,
                        "type", cu.get("relType")
                ));
            }
        }

        // ─────────────────────────────────────────────
        // 3️⃣ PaymentMethod Hub Nodes + main user edges
        // ─────────────────────────────────────────────
        @SuppressWarnings("unchecked")
        List<String> userPaymentMethods =
                (List<String>) raw.get("userPaymentMethods");

        if (userPaymentMethods != null) {
            for (String pm : userPaymentMethods) {
                String pmNodeId = "PM:" + pm;

                if (!addedNodeIds.contains(pmNodeId)) {
                    nodes.add(Map.of(
                            "id", pmNodeId,
                            "label", pm,
                            "type", "paymentMethod"    // ✅ frontend can style differently
                    ));
                    addedNodeIds.add(pmNodeId);
                }

                String edgeKey = mainUserId + "|USES_PAYMENT|" + pmNodeId;
                if (addedEdgeKeys.add(edgeKey)) {
                    edges.add(Map.of(
                            "source", mainUserId,
                            "target", pmNodeId,
                            "type", "USES_PAYMENT"
                    ));
                }
            }
        }

        // ─────────────────────────────────────────────
        // 4️⃣ Payment Peers (other users sharing methods)
        // ─────────────────────────────────────────────
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> paymentPeers =
                (List<Map<String, Object>>) raw.get("paymentPeers");

        if (paymentPeers != null) {
            for (Map<String, Object> pp : paymentPeers) {
                // ✅ OPTIONAL MATCH can yield null entries
                if (pp == null || pp.get("userId") == null) continue;

                String peerId = (String) pp.get("userId");
                String method = (String) pp.get("method");
                String pmNodeId = "PM:" + method;

                // Peer user node
                if (!addedNodeIds.contains(peerId)) {
                    nodes.add(Map.of(
                            "id", peerId,
                            "label", pp.get("name"),
                            "type", "user"
                    ));
                    addedNodeIds.add(peerId);
                }

                // Ensure PM hub node exists (might already be from step 3)
                if (!addedNodeIds.contains(pmNodeId)) {
                    nodes.add(Map.of(
                            "id", pmNodeId,
                            "label", method,
                            "type", "paymentMethod"
                    ));
                    addedNodeIds.add(pmNodeId);
                }

                // Edge: peer → PM hub (deduplicated)
                String edgeKey = peerId + "|USES_PAYMENT|" + pmNodeId;
                if (addedEdgeKeys.add(edgeKey)) {
                    edges.add(Map.of(
                            "source", peerId,
                            "target", pmNodeId,
                            "type", "USES_PAYMENT"
                    ));
                }
            }
        }

        // ─────────────────────────────────────────────
        // 5️⃣ Transactions  (unchanged)
        // ─────────────────────────────────────────────
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> transactions =
                (List<Map<String, Object>>) raw.get("transactions");

        if (transactions != null) {
            for (Map<String, Object> tx : transactions) {
                String txId = (String) tx.get("transactionId");

                if (!addedNodeIds.contains(txId)) {
                    nodes.add(Map.of(
                            "id", txId,
                            "label", txId,
                            "type", "transaction"
                    ));
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
        // 6️⃣ Receivers  (unchanged)
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
                        nodes.add(Map.of(
                                "id", receiverId,
                                "label", receiver.get("name"),
                                "type", "user"
                        ));
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

        return Map.of("nodes", nodes, "edges", edges);
    }
}