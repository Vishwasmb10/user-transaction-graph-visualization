package com.example.VisualizationSystem.service;

import com.example.VisualizationSystem.dto.TransactionRequest;
import com.example.VisualizationSystem.model.Transaction;
import com.example.VisualizationSystem.repository.GraphRelationshipRepository;
import com.example.VisualizationSystem.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public Transaction createOrUpdate(TransactionRequest request) {
        return transactionRepository.upsertTransaction(
                request.getTransactionId(),
                request.getAmount(),
                request.getIp(),
                request.getDeviceId()
        );

    }

    public List<Transaction> getAll() {
        return transactionRepository.findAll();
    }
}
