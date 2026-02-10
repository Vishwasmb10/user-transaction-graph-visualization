package com.example.VisualizationSystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TransactionRequest {

    @NotBlank
    private String transactionId;

    @NotNull
    private Double amount;

    @NotBlank
    private String senderId;

    @NotBlank
    private String receiverId;

    private String ip;
    private String deviceId;
}
