package com.example.VisualizationSystem.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class TransactionRequest {

    private String transactionId;
    private Double amount;
    private String currency;          // NEW — defaults to "USD" if null
    private String ip;
    private String deviceId;
    private String status;            // NEW — defaults to "PENDING" if null
    private String paymentMethod;     // NEW — "Visa", "PayPal", etc.

    private String senderId;
    private String receiverId;
}