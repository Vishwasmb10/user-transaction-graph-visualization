package com.example.VisualizationSystem.service;

import com.example.VisualizationSystem.dto.PageResponse;
import com.example.VisualizationSystem.dto.TransactionRequest;
import com.example.VisualizationSystem.model.Transaction;
import com.example.VisualizationSystem.repository.TransactionGraphRelationshipRepository;
import com.example.VisualizationSystem.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

        Transaction saved = transactionRepository.upsertTransaction(
                request.getTransactionId(),
                request.getAmount(),
                request.getCurrency(),
                request.getIp(),
                request.getDeviceId(),
                request.getStatus(),
                request.getPaymentMethod()
        );

        // ── SAME_IP linking ──
        if (existing == null || !Objects.equals(existing.getIp(), saved.getIp())) {
            graphRelationshipRepository.deleteSameIpLinks(saved.getTransactionId());
            if (saved.getIp() != null) {
                graphRelationshipRepository.linkTransactionsByIp(
                        saved.getTransactionId(), saved.getIp());
            }
        }

        // ── SAME_DEVICE linking ──
        if (existing == null || !Objects.equals(existing.getDeviceId(), saved.getDeviceId())) {
            graphRelationshipRepository.deleteSameDeviceLinks(saved.getTransactionId());
            if (saved.getDeviceId() != null) {
                graphRelationshipRepository.linkTransactionsByDevice(
                        saved.getTransactionId(), saved.getDeviceId());
            }
        }

        // ── SENT / RECEIVED_BY flow ──
        graphRelationshipRepository.linkTransactionFlow(
                request.getSenderId(),
                request.getReceiverId(),
                request.getTransactionId()
        );

        return saved;
    }

    public List<Transaction> getAll() {
        return transactionRepository.findAll();
    }

    public PageResponse<Transaction> getTransactionsPaged(
            String search,
            String ip,
            String deviceId,
            Double minAmount,
            Double maxAmount,
            String status,
            String paymentMethod,
            int page,
            int size
    ) {
        long total = transactionRepository.countTransactions(
                search, ip, deviceId, minAmount, maxAmount, status, paymentMethod);

        long skip = (long) page * size;

        if (skip >= total && total > 0) {
            page = 0;
            skip = 0;
        }

        List<Transaction> txs = transactionRepository.findTransactionsPaged(
                search, ip, deviceId, minAmount, maxAmount,
                status, paymentMethod, skip, size);

        return new PageResponse<>(txs, total, page, size);
    }
}