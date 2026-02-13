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

                // Same-IP transactions (enriched)
                CALL (tx) {
                    WITH tx
                    OPTIONAL MATCH (otherIpTx:Transaction)
                    WHERE otherIpTx.ip = tx.ip AND otherIpTx.transactionId <> tx.transactionId
                    RETURN collect(otherIpTx {
                        .transactionId, .amount, .currency,
                        .ip, .deviceId, .status, .paymentMethod,
                        timestamp: toString(otherIpTx.timestamp)
                    }) AS sameIpTransactions
                }

                // Same-device transactions (enriched)
                CALL (tx) {
                    WITH tx
                    OPTIONAL MATCH (otherDeviceTx:Transaction)
                    WHERE otherDeviceTx.deviceId = tx.deviceId AND otherDeviceTx.transactionId <> tx.transactionId
                    RETURN collect(otherDeviceTx {
                        .transactionId, .amount, .currency,
                        .ip, .deviceId, .status, .paymentMethod,
                        timestamp: toString(otherDeviceTx.timestamp)
                    }) AS sameDeviceTransactions
                }

                RETURN
                  tx {
                      .transactionId, .amount, .currency,
                      .ip, .deviceId, .status, .paymentMethod,
                      timestamp: toString(tx.timestamp)
                  } AS transaction,
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

        // ── 1. Main Transaction Node (enriched) ──
        @SuppressWarnings("unchecked")
        Map<String, Object> tx = (Map<String, Object>) raw.get("transaction");
        String mainTxId = (String) tx.get("transactionId");

        nodes.add(buildTxNode(tx));
        added.add(mainTxId);

        // ── 2. Senders ──
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> senders =
                (List<Map<String, Object>>) raw.get("senders");

        if (senders != null) {
            for (Map<String, Object> sender : senders) {
                String senderUserId = (String) sender.get("userId");
                if (!added.contains(senderUserId)) {
                    nodes.add(Map.of("id", senderUserId, "label", sender.get("name"), "type", "user"));
                    added.add(senderUserId);
                }
                edges.add(Map.of(
                        "id", senderUserId + "_" + mainTxId + "_SENT",
                        "source", senderUserId,
                        "target", mainTxId,
                        "type", "SENT"
                ));
            }
        }

        // ── 3. Receivers ──
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> receivers =
                (List<Map<String, Object>>) raw.get("receivers");

        if (receivers != null) {
            for (Map<String, Object> receiver : receivers) {
                String receiverUserId = (String) receiver.get("userId");
                if (!added.contains(receiverUserId)) {
                    nodes.add(Map.of("id", receiverUserId, "label", receiver.get("name"), "type", "user"));
                    added.add(receiverUserId);
                }
                edges.add(Map.of(
                        "id", mainTxId + "_" + receiverUserId + "_RECEIVED_BY",
                        "source", mainTxId,
                        "target", receiverUserId,
                        "type", "RECEIVED_BY"
                ));
            }
        }

        // ── 4. Same IP Transactions (enriched) ──
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sameIp =
                (List<Map<String, Object>>) raw.get("sameIpTransactions");

        if (sameIp != null) {
            for (Map<String, Object> other : sameIp) {
                if (other == null || other.get("transactionId") == null) continue;
                String otherId = (String) other.get("transactionId");

                if (!added.contains(otherId)) {
                    nodes.add(buildTxNode(other));
                    added.add(otherId);
                }

                edges.add(Map.of(
                        "id", mainTxId + "_" + otherId + "_SAME_IP",
                        "source", mainTxId,
                        "target", otherId,
                        "type", "SAME_IP"
                ));
            }
        }

        // ── 5. Same Device Transactions (enriched) ──
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sameDevice =
                (List<Map<String, Object>>) raw.get("sameDeviceTransactions");

        if (sameDevice != null) {
            for (Map<String, Object> other : sameDevice) {
                if (other == null || other.get("transactionId") == null) continue;
                String otherId = (String) other.get("transactionId");

                if (!added.contains(otherId)) {
                    nodes.add(buildTxNode(other));
                    added.add(otherId);
                }

                // Only add if not already added from sameIp
                String edgeId = mainTxId + "_" + otherId + "_SAME_DEVICE";
                edges.add(Map.of(
                        "id", edgeId,
                        "source", mainTxId,
                        "target", otherId,
                        "type", "SAME_DEVICE"
                ));
            }
        }

        return Map.of("nodes", nodes, "edges", edges);
    }

    /**
     * Build an enriched transaction node map from raw Cypher result.
     * Reusable for main tx and related txs.
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