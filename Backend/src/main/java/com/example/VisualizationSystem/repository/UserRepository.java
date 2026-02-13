package com.example.VisualizationSystem.repository;

import com.example.VisualizationSystem.dto.UserResponse;
import com.example.VisualizationSystem.model.User;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends Neo4jRepository<User, String> {

    // ✅ CHANGED: $paymentMethod → $paymentMethods (array property)
    @Query("""
        MERGE (u:User {userId: $userId})
        ON CREATE SET
            u.name = $name,
            u.email = $email,
            u.phone = $phone,
            u.address = $address,
            u.paymentMethods = $paymentMethods,
            u.createdAt = localdatetime()
        ON MATCH SET
            u.name = coalesce($name, u.name),
            u.email = coalesce($email, u.email),
            u.phone = coalesce($phone, u.phone),
            u.address = coalesce($address, u.address),
            u.paymentMethods = coalesce($paymentMethods, u.paymentMethods)
        RETURN u
    """)
    User upsertUser(@Param("userId") String userId,
                    @Param("name") String name,
                    @Param("email") String email,
                    @Param("phone") String phone,
                    @Param("address") String address,
                    @Param("paymentMethods") List<String> paymentMethods);

    // ✅ CHANGED: projection field name
    @Query("""
        MATCH (u:User)
        RETURN
            u.name AS name,
            u.email AS email,
            u.phone AS phone,
            u.address AS address,
            u.paymentMethods AS paymentMethods,
            u.createdAt AS createdAt
    """)
    List<UserResponse> findAllUserDtos();

    // ✅ ADDED: optional paymentMethod filter using IN on the array
    @Query("""
        MATCH (u:User)
        WHERE ($email IS NULL OR u.email = $email)
          AND ($phone IS NULL OR u.phone = $phone)
          AND ($paymentMethod IS NULL OR $paymentMethod IN u.paymentMethods)
        RETURN u
        SKIP $skip
        LIMIT $limit
    """)
    List<User> findUsersPaged(
            @Param("email") String email,
            @Param("phone") String phone,
            @Param("paymentMethod") String paymentMethod,
            @Param("skip") long skip,
            @Param("limit") long limit
    );

    // ✅ ADDED: matching count query
    @Query("""
        MATCH (u:User)
        WHERE ($email IS NULL OR u.email = $email)
          AND ($phone IS NULL OR u.phone = $phone)
          AND ($paymentMethod IS NULL OR $paymentMethod IN u.paymentMethods)
        RETURN count(u)
    """)
    long countUsers(
            @Param("email") String email,
            @Param("phone") String phone,
            @Param("paymentMethod") String paymentMethod
    );
}