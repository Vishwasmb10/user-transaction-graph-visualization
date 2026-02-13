package com.example.VisualizationSystem.model;

import lombok.*;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.LocalDateTime;

@Node("Transaction")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class Transaction {

    @Id
    private String transactionId;

    private Double amount;
    private String currency;          // NEW: "USD", "EUR", "GBP"
    private LocalDateTime timestamp;
    private String ip;
    private String deviceId;
    private String status;            // NEW: "COMPLETED", "PENDING", "FAILED", "FLAGGED", "REVERSED"
    private String paymentMethod;     // NEW: "Visa", "PayPal", etc. — stored as property on the node
}