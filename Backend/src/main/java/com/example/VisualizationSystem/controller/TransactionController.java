package com.example.VisualizationSystem.controller;

import com.example.VisualizationSystem.dto.PageResponse;
import com.example.VisualizationSystem.dto.TransactionRequest;
import com.example.VisualizationSystem.model.Transaction;
import com.example.VisualizationSystem.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<Transaction> create(@RequestBody TransactionRequest request) {
        return ResponseEntity.ok(transactionService.createOrUpdate(request));
    }

    @GetMapping
    public ResponseEntity<PageResponse<Transaction>> list(
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) Double minAmount,
            @RequestParam(required = false) Double maxAmount,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(
                transactionService.getTransactionsPaged(
                        blankToNull(ip),
                        blankToNull(deviceId),
                        minAmount,
                        maxAmount,
                        blankToNull(status),
                        blankToNull(paymentMethod),
                        page, size)
        );
    }

    /** Convert empty/blank strings to null so Cypher IS NULL checks work */
    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}