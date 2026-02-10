package com.example.VisualizationSystem.repository;

import com.example.VisualizationSystem.model.Transaction;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface TransactionRepository extends Neo4jRepository<Transaction, String> {

    @Query("""
        MERGE (t:Transaction {transactionId: $transactionId})
        ON CREATE SET
            t.amount = $amount,
            t.ip = $ip,
            t.deviceId = $deviceId,
            t.timestamp = localdatetime()
        ON MATCH SET
            t.amount = coalesce($amount, t.amount),
            t.ip = CASE WHEN $ip IS NOT NULL AND $ip <> '' THEN $ip ELSE t.ip END,
            t.deviceId = CASE WHEN $deviceId IS NOT NULL AND $deviceId <> '' THEN $deviceId ELSE t.deviceId END
        RETURN t
    """)
    Transaction upsertTransaction(
            String transactionId,
            Double amount,
            String ip,
            String deviceId
    );
}
