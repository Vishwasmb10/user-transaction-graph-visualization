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
                     [(u)-[r:SAME_EMAIL|SAME_PHONE|SAME_ADDRESS|SAME_PAYMENT_METHOD]-(other:User) |
                        {
                          userId: other.userId,
                          name: other.name,
                          relType: type(r)
                        }
                     ] AS connectedUsers
                CALL (u) {
                    WITH u
                    MATCH (u)-[:SENT]->(tx:Transaction)-[:RECEIVED_BY]->(receiver:User)
                    RETURN collect(tx { .transactionId, .amount }) AS transactions,
                           collect(receiver { .userId, .name }) AS receivers
                }
                RETURN u { .userId, .name, .email } AS user,
                       connectedUsers,
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

        // -------------------------
        // 1️⃣ Add Main User Node
        // -------------------------
        Map<String, Object> user = (Map<String, Object>) raw.get("user");

        String mainUserId = (String) user.get("userId");

        nodes.add(Map.of(
                "id", mainUserId,
                "label", user.get("name"),
                "type", "user"
        ));

        addedNodeIds.add(mainUserId);

        // -------------------------
        // 2️⃣ Add Connected Users
        // -------------------------
        List<Map<String, Object>> connectedUsers =
                (List<Map<String, Object>>) raw.get("connectedUsers");

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

        // -------------------------
        // 3️⃣ Add Transactions
        // -------------------------
        List<Map<String, Object>> transactions =
                (List<Map<String, Object>>) raw.get("transactions");

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

        // -------------------------
        // 4️⃣ Add Receivers
        // -------------------------
        List<Map<String, Object>> receivers =
                (List<Map<String, Object>>) raw.get("receivers");

        for (int i = 0; i < transactions.size(); i++) {

            Map<String, Object> tx = transactions.get(i);
            String txId = (String) tx.get("transactionId");

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

        return Map.of(
                "nodes", nodes,
                "edges", edges
        );
    }

}
