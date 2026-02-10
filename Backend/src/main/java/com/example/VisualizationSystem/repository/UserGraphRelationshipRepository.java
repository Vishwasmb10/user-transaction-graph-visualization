package com.example.VisualizationSystem.repository;

import com.example.VisualizationSystem.model.User;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

public interface UserGraphRelationshipRepository extends Neo4jRepository<User, String> {

    // =====================================================
    // USER ↔ USER (SHARED ATTRIBUTES — SINGLE EDGE ONLY)
    // =====================================================

    @Query("""
                MATCH (u:User {userId: $userId, email: $email})
                MATCH (other:User {email: $email})
                WHERE u.userId <> other.userId
                MERGE (u)-[:SAME_EMAIL]->(other)
            """)
    void linkUsersByEmail(@Param("userId") String userId,
                          @Param("email") String email);


    @Query("""
                MATCH (u:User {userId: $userId, phone: $phone})
                MATCH (other:User {phone: $phone})
                WHERE u.userId <> other.userId
                MERGE (u)-[:SAME_PHONE]->(other)
            """)
    void linkUsersByPhone(@Param("userId") String userId,
                          @Param("phone") String phone);

    @Query("""
                MATCH (u:User {userId: $userId, address: $address})
                MATCH (other:User {address: $address})
                WHERE u.userId <> other.userId
                MERGE (u)-[:SAME_ADDRESS]->(other)
            """)
    void linkUsersByAddress(@Param("userId") String userId,
                            @Param("address") String address);


    @Query("""
                MATCH (u:User {userId: $userId, paymentMethod: $paymentMethod})
                MATCH (other:User {paymentMethod: $paymentMethod})
                WHERE u.userId <> other.userId
                MERGE (u)-[:SAME_PAYMENT_METHOD]->(other)
            """)
    void linkUsersByPaymentMethod(@Param("userId") String userId,
                                  @Param("paymentMethod") String paymentMethod);


  }
