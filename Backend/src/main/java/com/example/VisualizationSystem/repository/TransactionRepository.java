package com.example.VisualizationSystem.repository;

import com.example.VisualizationSystem.model.Transaction;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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

    @Query("""
            MATCH (t:Transaction)
            WHERE ($ip IS NULL OR t.ip = $ip)
              AND ($deviceId IS NULL OR t.deviceId = $deviceId)
              AND ($minAmount IS NULL OR t.amount >= $minAmount)
              AND ($maxAmount IS NULL OR t.amount <= $maxAmount)
            RETURN t
            SKIP $skip
            LIMIT $limit
            """)
    List<Transaction> findTransactionsPaged(
            @Param("ip") String ip,
            @Param("deviceId") String deviceId,
            @Param("minAmount") Double minAmount,
            @Param("maxAmount") Double maxAmount,
            @Param("skip") long skip,
            @Param("limit") long limit
    );

    @Query("""
            MATCH (t:Transaction)
            WHERE ($ip IS NULL OR t.ip = $ip)
              AND ($deviceId IS NULL OR t.deviceId = $deviceId)
              AND ($minAmount IS NULL OR t.amount >= $minAmount)
              AND ($maxAmount IS NULL OR t.amount <= $maxAmount)
            RETURN count(t)
            """)
    long countTransactions(
            @Param("ip") String ip,
            @Param("deviceId") String deviceId,
            @Param("minAmount") Double minAmount,
            @Param("maxAmount") Double maxAmount
    );

}
