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
            t.currency = coalesce($currency, 'USD'),
            t.ip = $ip,
            t.deviceId = $deviceId,
            t.status = coalesce($status, 'PENDING'),
            t.paymentMethod = $paymentMethod,
            t.timestamp = localdatetime()
        ON MATCH SET
            t.amount = coalesce($amount, t.amount),
            t.currency = coalesce($currency, t.currency),
            t.ip = CASE WHEN $ip IS NOT NULL AND $ip <> '' THEN $ip ELSE t.ip END,
            t.deviceId = CASE WHEN $deviceId IS NOT NULL AND $deviceId <> '' THEN $deviceId ELSE t.deviceId END,
            t.status = coalesce($status, t.status),
            t.paymentMethod = coalesce($paymentMethod, t.paymentMethod)
        RETURN t
    """)
    Transaction upsertTransaction(
            @Param("transactionId") String transactionId,
            @Param("amount") Double amount,
            @Param("currency") String currency,
            @Param("ip") String ip,
            @Param("deviceId") String deviceId,
            @Param("status") String status,
            @Param("paymentMethod") String paymentMethod
    );

    @Query("""
        MATCH (t:Transaction)
        WHERE ($ip IS NULL OR $ip = '' OR t.ip = $ip)
          AND ($deviceId IS NULL OR $deviceId = '' OR t.deviceId = $deviceId)
          AND ($minAmount IS NULL OR t.amount >= $minAmount)
          AND ($maxAmount IS NULL OR t.amount <= $maxAmount)
          AND ($status IS NULL OR $status = '' OR t.status = $status)
          AND ($paymentMethod IS NULL OR $paymentMethod = '' OR t.paymentMethod = $paymentMethod)
        RETURN t
        ORDER BY t.timestamp DESC
        SKIP $skip
        LIMIT $limit
    """)
    List<Transaction> findTransactionsPaged(
            @Param("ip") String ip,
            @Param("deviceId") String deviceId,
            @Param("minAmount") Double minAmount,
            @Param("maxAmount") Double maxAmount,
            @Param("status") String status,
            @Param("paymentMethod") String paymentMethod,
            @Param("skip") long skip,
            @Param("limit") long limit
    );

    @Query("""
        MATCH (t:Transaction)
        WHERE ($ip IS NULL OR $ip = '' OR t.ip = $ip)
          AND ($deviceId IS NULL OR $deviceId = '' OR t.deviceId = $deviceId)
          AND ($minAmount IS NULL OR t.amount >= $minAmount)
          AND ($maxAmount IS NULL OR t.amount <= $maxAmount)
          AND ($status IS NULL OR $status = '' OR t.status = $status)
          AND ($paymentMethod IS NULL OR $paymentMethod = '' OR t.paymentMethod = $paymentMethod)
        RETURN count(t)
    """)
    long countTransactions(
            @Param("ip") String ip,
            @Param("deviceId") String deviceId,
            @Param("minAmount") Double minAmount,
            @Param("maxAmount") Double maxAmount,
            @Param("status") String status,
            @Param("paymentMethod") String paymentMethod
    );
}