package com.example.VisualizationSystem.repository;

import com.example.VisualizationSystem.model.User;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserGraphRelationshipRepository extends Neo4jRepository<User, String> {

    // ── Scalar attributes (unchanged) ──

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

    // ✅ REPLACED: pairwise SAME_PAYMENT_METHOD → hub-and-spoke USES_PAYMENT

    @Query("""
        MATCH (u:User {userId: $userId})
        UNWIND $paymentMethods AS pm
        MERGE (p:PaymentMethod {name: pm})
        MERGE (u)-[:USES_PAYMENT]->(p)
    """)
    void linkUserPaymentMethods(@Param("userId") String userId,
                                @Param("paymentMethods") List<String> paymentMethods);

    // ── Delete helpers ──

    @Query("MATCH (u:User {userId: $userId})-[r:SAME_EMAIL]-() DELETE r")
    void deleteSameEmailLinks(String userId);

    @Query("MATCH (u:User {userId: $userId})-[r:SAME_PHONE]-() DELETE r")
    void deleteSamePhoneLinks(String userId);

    @Query("MATCH (u:User {userId: $userId})-[r:SAME_ADDRESS]-() DELETE r")
    void deleteSameAddressLinks(String userId);

    // ✅ CHANGED: delete USES_PAYMENT edges (not SAME_PAYMENT_METHOD)
    @Query("MATCH (u:User {userId: $userId})-[r:USES_PAYMENT]->() DELETE r")
    void deletePaymentLinks(String userId);
}