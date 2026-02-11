package com.example.VisualizationSystem.dto;

import com.example.VisualizationSystem.model.Transaction;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionEdgeData {
    private String senderId;
    private String receiverId;
    private Transaction transaction;
}