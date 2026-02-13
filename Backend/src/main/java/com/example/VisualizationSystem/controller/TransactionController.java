package com.example.VisualizationSystem.controller;

import com.example.VisualizationSystem.dto.PageResponse;
import com.example.VisualizationSystem.dto.TransactionRequest;
import com.example.VisualizationSystem.exception.BadRequestException;
import com.example.VisualizationSystem.exception.ResourceNotFoundException;
import com.example.VisualizationSystem.model.Transaction;
import com.example.VisualizationSystem.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;



/**
 * REST API controller for managing transactions.
 * Provides CRUD operations and filtering capabilities for transactions.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Validated
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * Creates a new transaction or updates an existing one.
     * 
     * @param request Transaction data
     * @return Created or updated transaction
     */
    @PostMapping
    public ResponseEntity<Transaction> createOrUpdateTransaction( @RequestBody TransactionRequest request) {
        log.info("Creating/updating transaction: {}", request);
        
        if (request == null) {
            throw new BadRequestException("Transaction request cannot be null");
        }
        
        try {
            Transaction transaction = transactionService.createOrUpdate(request);
            
            // Return 201 for creation, 200 for update
            HttpStatus status = request.getTransactionId() == null ? HttpStatus.CREATED : HttpStatus.OK;
            
            log.info("Transaction {} successfully: {}", 
                    status == HttpStatus.CREATED ? "created" : "updated", 
                    transaction.getTransactionId());
            
            return ResponseEntity.status(status).body(transaction);
            
        } catch (Exception e) {
            log.error("Error creating/updating transaction", e);
            throw new BadRequestException("Failed to process transaction: " + e.getMessage());
        }
    }

    /**
     * Retrieves a paginated and filtered list of transactions.
     * Supports multiple filter criteria and sorting options.
     * 
     * @param search General search term
     * @param ip IP address filter
     * @param deviceId Device ID filter
     * @param minAmount Minimum amount filter
     * @param maxAmount Maximum amount filter
     * @param status Transaction status filter
     * @param paymentMethod Payment method filter
     * @param page Page number (0-indexed)
     * @param size Page size
     * @param sortBy Field to sort by
     * @param sortDir Sort direction (asc/desc)
     * @return Paginated list of transactions
     */
    @GetMapping
    public ResponseEntity<PageResponse<Transaction>> listTransactions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) Double minAmount,
            @RequestParam(required = false) Double maxAmount,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        log.info("Fetching transactions - page: {}, size: {}, sortBy: {}, sortDir: {}", 
                page, size, sortBy, sortDir);
        
        // Validate sort direction
        if (!sortDir.equalsIgnoreCase("asc") && !sortDir.equalsIgnoreCase("desc")) {
            throw new BadRequestException("Sort direction must be either 'asc' or 'desc'");
        }
        
        // Validate amount range
        if (minAmount != null && maxAmount != null && minAmount > maxAmount) {
            throw new BadRequestException("Minimum amount cannot be greater than maximum amount");
        }
        
        try {
            PageResponse<Transaction> response = transactionService.getTransactionsPaged(
                    sanitizeInput(search),
                    sanitizeInput(ip),
                    sanitizeInput(deviceId),
                    minAmount,
                    maxAmount,
                    sanitizeInput(status),
                    sanitizeInput(paymentMethod),
                    page,
                    size,
                    sortBy,
                    sortDir
            );
            
            log.info("Successfully retrieved {} transactions", response.getContent().size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error fetching transactions", e);
            throw new BadRequestException("Failed to fetch transactions: " + e.getMessage());
        }
    }

    /**
     * Retrieves a specific transaction by ID.
     * 
     * @param id Transaction ID
     * @return Transaction details
     */
    @GetMapping("/{id}")
    public ResponseEntity<Transaction> getTransactionById(@PathVariable String id) {
        log.info("Fetching transaction with id: {}", id);
        
        if (id == null || id.trim().isEmpty()) {
            throw new BadRequestException("Transaction ID cannot be empty");
        }
        
        Transaction transaction = transactionService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));
        
        log.info("Successfully retrieved transaction: {}", id);
        return ResponseEntity.ok(transaction);
    }


    /**
     * Sanitizes input strings by trimming and converting blank to null.
     * 
     * @param input Input string
     * @return Sanitized string or null
     */
    private String sanitizeInput(String input) {
        return (input == null || input.isBlank()) ? null : input.trim();
    }
}
