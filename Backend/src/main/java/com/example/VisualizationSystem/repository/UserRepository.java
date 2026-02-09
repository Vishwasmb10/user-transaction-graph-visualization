package com.example.VisualizationSystem.repository;

import com.example.VisualizationSystem.dto.UserResponse;
import com.example.VisualizationSystem.model.User;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
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
                    u.paymentMethod = $paymentMethod,
                    u.createdAt = localdatetime()
                ON MATCH SET
                    u.name = coalesce($name, u.name),
                    u.email = coalesce($email, u.email),
                    u.phone = coalesce($phone, u.phone),
                    u.address = coalesce($address, u.address),
                    u.paymentMethod = coalesce($paymentMethod, u.paymentMethod)
                RETURN u
            """)
    User upsertUser(String userId,
                    String name,
                    String email,
                    String phone,
                    String address,
                    String paymentMethod);

    @Query("""
                MATCH (u:User)
                RETURN 
                    u.name AS name,
                    u.email AS email,
                    u.phone AS phone,
                    u.address AS address,
                    u.paymentMethod AS paymentMethod,
                    u.createdAt AS createdAt
            """)
    List<UserResponse> findAllUserDtos();

}
