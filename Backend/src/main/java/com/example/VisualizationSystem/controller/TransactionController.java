package com.example.VisualizationSystem.controller;

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

    @GetMapping
    public List<Transaction> getAll() {
        return transactionService.getAll();
    }
}
