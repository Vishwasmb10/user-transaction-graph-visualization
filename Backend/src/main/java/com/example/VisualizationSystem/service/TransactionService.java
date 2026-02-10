package com.example.VisualizationSystem.service;

import com.example.VisualizationSystem.dto.TransactionRequest;
import com.example.VisualizationSystem.model.Transaction;
import com.example.VisualizationSystem.repository.TransactionGraphRelationshipRepository;
import com.example.VisualizationSystem.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionGraphRelationshipRepository graphRelationshipRepository;

    public Transaction createOrUpdate(TransactionRequest request) {

        var saved=transactionRepository.upsertTransaction(
                request.getTransactionId(),
                request.getAmount(),
                request.getIp(),
                request.getDeviceId()
        );
        graphRelationshipRepository.linkTransactionFlow(request.getSenderId(),request.getReceiverId(),request.getTransactionId());
        graphRelationshipRepository.linkTransactionsByIp(saved.getTransactionId(),saved.getIp());
        graphRelationshipRepository.linkTransactionsByDevice(saved.getTransactionId(),saved.getDeviceId());
        return saved;

    }

    public List<Transaction> getAll() {
        return transactionRepository.findAll();
    }
}
