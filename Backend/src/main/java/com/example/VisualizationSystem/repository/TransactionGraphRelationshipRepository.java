package com.example.VisualizationSystem.repository;

import com.example.VisualizationSystem.model.Transaction;
import com.example.VisualizationSystem.model.User;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionGraphRelationshipRepository extends Neo4jRepository<Transaction, String> {

    // =====================================================
    // USER → TRANSACTION → USER (DIRECT MONEY FLOW)
    // =====================================================

    @Query("""
                MATCH (sender:User {userId: $senderId})
                MATCH (receiver:User {userId: $receiverId})
                MATCH (tx:Transaction {transactionId: $txId})
            
                MERGE (sender)-[:SENT]->(tx)
                MERGE (tx)-[:RECEIVED_BY]->(receiver)
                MERGE (sender)-[:TRANSFERRED_TO]->(receiver)
            """)
    void linkTransactionFlow(@Param("senderId") String senderId,
                             @Param("receiverId") String receiverId,
                             @Param("txId") String txId);


    // =====================================================
    // TRANSACTION ↔ TRANSACTION (SHARED IP — SINGLE EDGE)
    // =====================================================

    @Query("""
               MATCH (tx:Transaction {transactionId: $txId})
               MATCH (other:Transaction {ip: $ip})
               WHERE other.transactionId <> $txId
               MERGE (tx)-[:SAME_IP]->(other)
            
            """)
    void linkTransactionsByIp(@Param("txId") String txId,
                              @Param("ip") String ip);


    // =====================================================
    // TRANSACTION ↔ TRANSACTION (SHARED DEVICE — SINGLE EDGE)
    // =====================================================

    @Query("""
            MATCH (tx:Transaction {transactionId: $txId})
            MATCH (other:Transaction {deviceId: $deviceId})
            WHERE other.transactionId <> $txId
            MERGE (tx)-[:SAME_DEVICE]->(other)
            
            """)
    void linkTransactionsByDevice(@Param("txId") String txId,
                                  @Param("deviceId") String deviceId);

    @Query("""
            MATCH (t:Transaction {transactionId: $txId})-[r:SAME_IP]-()
            DELETE r
            """)
    void deleteSameIpLinks(String txId);


    @Query("""
            MATCH (t:Transaction {transactionId: $txId})-[r:SAME_DEVICE]-()
            DELETE r
            """)
    void deleteSameDeviceLinks(String txId);

}
