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

    @Query("""
        MATCH (u:User)
        WHERE ($search IS NULL OR $search = ''
               OR u.userId CONTAINS $search
               OR toLower(u.name) CONTAINS toLower($search))
          AND ($email IS NULL OR $email = '' OR u.email = $email)
          AND ($phone IS NULL OR $phone = '' OR u.phone = $phone)
          AND ($paymentMethod IS NULL OR $paymentMethod = '' OR $paymentMethod IN u.paymentMethods)
        RETURN u
        ORDER BY u.createdAt DESC
        SKIP $skip
        LIMIT $limit
    """)
    List<User> findUsersPaged(
            @Param("search") String search,
            @Param("email") String email,
            @Param("phone") String phone,
            @Param("paymentMethod") String paymentMethod,
            @Param("skip") long skip,
            @Param("limit") long limit
    );

    @Query("""
        MATCH (u:User)
        WHERE ($search IS NULL OR $search = ''
               OR u.userId CONTAINS $search
               OR toLower(u.name) CONTAINS toLower($search))
          AND ($email IS NULL OR $email = '' OR u.email = $email)
          AND ($phone IS NULL OR $phone = '' OR u.phone = $phone)
          AND ($paymentMethod IS NULL OR $paymentMethod = '' OR $paymentMethod IN u.paymentMethods)
        RETURN count(u)
    """)
    long countUsers(
            @Param("search") String search,
            @Param("email") String email,
            @Param("phone") String phone,
            @Param("paymentMethod") String paymentMethod
    );
}