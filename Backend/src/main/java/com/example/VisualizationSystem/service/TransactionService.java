package com.example.VisualizationSystem.service;

import com.example.VisualizationSystem.dto.PageResponse;
import com.example.VisualizationSystem.dto.TransactionRequest;
import com.example.VisualizationSystem.model.Transaction;
import com.example.VisualizationSystem.repository.TransactionGraphRelationshipRepository;
import com.example.VisualizationSystem.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionGraphRelationshipRepository graphRelationshipRepository;

    public Transaction createOrUpdate(TransactionRequest request) {
        Transaction existing =
                transactionRepository.findById(request.getTransactionId()).orElse(null);
        var saved = transactionRepository.upsertTransaction(
                request.getTransactionId(),
                request.getAmount(),
                request.getIp(),
                request.getDeviceId()
        );
        if (existing == null || !Objects.equals(existing.getIp(), saved.getIp())) {
            graphRelationshipRepository.deleteSameIpLinks(saved.getTransactionId());

            if (saved.getIp() != null) {
                graphRelationshipRepository.linkTransactionsByIp(saved.getTransactionId(), saved.getIp());
            }
        }

        // SAME_DEVICE
        if (existing == null || !Objects.equals(existing.getDeviceId(), saved.getDeviceId())) {
            graphRelationshipRepository.deleteSameDeviceLinks(saved.getTransactionId());

            if (saved.getDeviceId() != null) {
                graphRelationshipRepository.linkTransactionsByDevice(saved.getTransactionId(), saved.getDeviceId());
            }
        }
        graphRelationshipRepository.linkTransactionFlow(request.getSenderId(), request.getReceiverId(), request.getTransactionId());
        return saved;

    }

    public List<Transaction> getAll() {
        return transactionRepository.findAll();
    }

    public PageResponse<Transaction> getTransactionsPaged(
            String ip,
            String deviceId,
            Double minAmount,
            Double maxAmount,
            int page,
            int size
    ) {
        long skip = (long) page * size;

        List<Transaction> txs = transactionRepository.findTransactionsPaged(
                ip, deviceId, minAmount, maxAmount, skip, size
        );

        long total = transactionRepository.countTransactions(
                ip, deviceId, minAmount, maxAmount
        );

        return new PageResponse<>(txs, total, page, size);
    }

}
