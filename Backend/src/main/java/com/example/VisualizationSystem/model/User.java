package com.example.VisualizationSystem.model;

import lombok.*;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.LocalDateTime;
import java.util.List;

@Node("User")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class User {

    @Id
    private String userId;

    private String name;
    private String email;
    private String phone;
    private String address;
    private List<String> paymentMethods ;
    private LocalDateTime createdAt;
}
