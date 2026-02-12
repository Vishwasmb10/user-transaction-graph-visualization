package com.example.VisualizationSystem.controller;

import com.example.VisualizationSystem.dto.PageResponse;
import com.example.VisualizationSystem.dto.TransactionRequest;
import com.example.VisualizationSystem.model.Transaction;
import com.example.VisualizationSystem.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public Transaction createOrUpdate(@Valid @RequestBody TransactionRequest request) {
        return transactionService.createOrUpdate(request);
    }

//    @GetMapping
//    public List<Transaction> getAll() {
//        return transactionService.getAll();
//    }

    @GetMapping
    public PageResponse<Transaction> getTransactions(
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) Double minAmount,
            @RequestParam(required = false) Double maxAmount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        if (ip != null || deviceId != null || minAmount != null || maxAmount != null) {
            page = 0;
        }

        return transactionService.getTransactionsPaged(
                ip, deviceId, minAmount, maxAmount, page, size
        );
    }

}
