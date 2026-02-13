package com.example.VisualizationSystem.controller;

import com.example.VisualizationSystem.exception.ResourceNotFoundException;
import com.example.VisualizationSystem.service.TransactionRelationshipService;
import com.example.VisualizationSystem.service.UserRelationshipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API controller for managing relationships in the graph database.
 * Provides endpoints to retrieve connection graphs for users and transactions.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/relationships")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RelationshipController {

    private final UserRelationshipService userRelationshipService;
    private final TransactionRelationshipService transactionRelationshipService;

    /**
     * Retrieves the relationship graph for a specific user.
     * Returns all connected nodes and relationships.
     * 
     * @param id User identifier
     * @return Graph structure containing nodes and relationships
     */
    @GetMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> getUserRelationships(@PathVariable String id) {
        log.info("Fetching relationship graph for user: {}", id);
        
        if (id == null || id.trim().isEmpty()) {
            throw new ResourceNotFoundException("User ID cannot be empty");
        }
        
        try {
            Map<String, Object> graph = userRelationshipService.getUserGraph(id);
            
            if (graph == null || graph.isEmpty()) {
                throw new ResourceNotFoundException("User", "id", id);
            }
            
            log.info("Successfully retrieved graph for user: {}", id);
            return ResponseEntity.ok(graph);
            
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching user relationships for id: {}", id, e);
            throw new ResourceNotFoundException("User", "id", id);
        }
    }

    /**
     * Retrieves the relationship graph for a specific transaction.
     * Returns all connected nodes and relationships.
     * 
     * @param id Transaction identifier
     * @return Graph structure containing nodes and relationships
     */
    @GetMapping("/transactions/{id}")
    public ResponseEntity<Map<String, Object>> getTransactionRelationships(@PathVariable String id) {
        log.info("Fetching relationship graph for transaction: {}", id);
        
        if (id == null || id.trim().isEmpty()) {
            throw new ResourceNotFoundException("Transaction ID cannot be empty");
        }
        
        try {
            Map<String, Object> graph = transactionRelationshipService.getTransactionGraph(id);
            
            if (graph == null || graph.isEmpty()) {
                throw new ResourceNotFoundException("Transaction", "id", id);
            }
            
            log.info("Successfully retrieved graph for transaction: {}", id);
            return ResponseEntity.ok(graph);
            
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching transaction relationships for id: {}", id, e);
            throw new ResourceNotFoundException("Transaction", "id", id);
        }
    }

}
