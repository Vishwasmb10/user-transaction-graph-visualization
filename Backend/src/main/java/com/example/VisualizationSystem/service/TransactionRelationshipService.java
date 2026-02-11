package com.example.VisualizationSystem.service;

import com.example.VisualizationSystem.repository.TransactionGraphRelationshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TransactionRelationshipService {
    private final Neo4jClient neo4jClient;

    private final TransactionGraphRelationshipRepository graphRepo;

    public Map<String, Object> getTransactionGraph(String txId) {

        String query = """
                    MATCH (tx:Transaction {transactionId: $txId})
                
                WITH tx,
                     [(sender:User)-[:SENT]->(tx) | sender { .userId, .name }] AS senders,
                     [(tx)-[:RECEIVED_BY]->(receiver:User) | receiver { .userId, .name }] AS receivers
                
                // Collect same-IP transactions
                CALL (tx) {
                    WITH tx
                    MATCH (otherIpTx:Transaction)
                    WHERE otherIpTx.ip = tx.ip AND otherIpTx.transactionId <> tx.transactionId
                    RETURN collect(otherIpTx { .transactionId, .amount }) AS sameIpTransactions
                }
                
                // Collect same-device transactions
                CALL (tx) {
                    WITH tx
                    MATCH (otherDeviceTx:Transaction)
                    WHERE otherDeviceTx.deviceId = tx.deviceId AND otherDeviceTx.transactionId <> tx.transactionId
                    RETURN collect(otherDeviceTx { .transactionId, .amount }) AS sameDeviceTransactions
                }
                
                RETURN
                  tx { .transactionId, .amount, .ip, .deviceId } AS transaction,
                  senders,
                  receivers,
                  sameIpTransactions,
                  sameDeviceTransactions
                
                """;


        Map<String, Object> raw = neo4jClient.query(query)
                .bind(txId).to("txId")
                .fetch()
                .one()
                .orElse(Map.of());

        if (raw.isEmpty()) {
            return Map.of("nodes", List.of(), "edges", List.of());
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        Set<String> added = new HashSet<>();

        Map<String, Object> tx = (Map<String, Object>) raw.get("transaction");
        String mainTxId = (String) tx.get("transactionId");

        // Add main transaction node
        nodes.add(Map.of(
                "id", mainTxId,
                "label", mainTxId,
                "type", "transaction"
        ));
        added.add(mainTxId);

        // Senders
        List<Map<String, Object>> senders =
                (List<Map<String, Object>>) raw.get("senders");

        for (Map<String, Object> sender : senders) {
            String userId = (String) sender.get("userId");

            if (!added.contains(userId)) {
                nodes.add(Map.of(
                        "id", userId,
                        "label", sender.get("name"),
                        "type", "user"
                ));
                added.add(userId);
            }

            edges.add(Map.of(
                    "id", userId + "_" + mainTxId + "_SENT",
                    "source", userId,
                    "target", mainTxId,
                    "type", "SENT"
            ));
        }

        // Receivers
        List<Map<String, Object>> receivers =
                (List<Map<String, Object>>) raw.get("receivers");

        for (Map<String, Object> receiver : receivers) {
            String userId = (String) receiver.get("userId");

            if (!added.contains(userId)) {
                nodes.add(Map.of(
                        "id", userId,
                        "label", receiver.get("name"),
                        "type", "user"
                ));
                added.add(userId);
            }

            edges.add(Map.of(
                    "id", mainTxId + "_" + userId + "_RECEIVED_BY",
                    "source", mainTxId,
                    "target", userId,
                    "type", "RECEIVED_BY"
            ));
        }

        // Same IP transactions
        List<Map<String, Object>> sameIp =
                (List<Map<String, Object>>) raw.get("sameIpTransactions");

        for (Map<String, Object> other : sameIp) {
            String otherId = (String) other.get("transactionId");

            if (!added.contains(otherId)) {
                nodes.add(Map.of(
                        "id", otherId,
                        "label", otherId,
                        "type", "transaction"
                ));
                added.add(otherId);
            }

            edges.add(Map.of(
                    "id", mainTxId + "_" + otherId + "_SAME_IP",
                    "source", mainTxId,
                    "target", otherId,
                    "type", "SAME_IP"
            ));
        }

        // Same Device transactions
        List<Map<String, Object>> sameDevice =
                (List<Map<String, Object>>) raw.get("sameDeviceTransactions");

        for (Map<String, Object> other : sameDevice) {
            String otherId = (String) other.get("transactionId");

            if (!added.contains(otherId)) {
                nodes.add(Map.of(
                        "id", otherId,
                        "label", otherId,
                        "type", "transaction"
                ));
                added.add(otherId);
            }

            edges.add(Map.of(
                    "id", mainTxId + "_" + otherId + "_SAME_DEVICE",
                    "source", mainTxId,
                    "target", otherId,
                    "type", "SAME_DEVICE"
            ));
        }

        return Map.of(
                "nodes", nodes,
                "edges", edges
        );
    }

}
