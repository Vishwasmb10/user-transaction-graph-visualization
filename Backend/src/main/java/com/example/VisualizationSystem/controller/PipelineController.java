package com.example.VisualizationSystem.controller;

import com.example.VisualizationSystem.dto.ApiResponse;
import com.example.VisualizationSystem.exception.BadRequestException;
import com.example.VisualizationSystem.pipeline.GraphPipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API controller for managing data pipeline operations.
 * Handles bulk data operations for the graph database.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final GraphPipelineService pipelineService;

    /**
     * Executes the main data pipeline to generate and insert test data.
     * Creates 100k transaction nodes and 10k users with relationship mappings.
     * 
     * @return Response with success message
     */
    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<String>> runPipeline() {
        log.info("Starting data pipeline execution");
        
        try {
            pipelineService.runPipeline();
            log.info("Data pipeline completed successfully");
            
            ApiResponse<String> response = ApiResponse.<String>builder()
                    .success(true)
                    .message("Pipeline executed successfully")
                    .data("100k transaction nodes and 10k users with relation mapping inserted")
                    .build();
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            log.error("Error executing pipeline", e);
            throw new BadRequestException("Failed to execute pipeline: " + e.getMessage());
        }
    }

    /**
     * Deletes all data from the graph database.
     * This operation is irreversible.
     * 
     * @return Response indicating successful deletion
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteAllData() {
        log.info("Deleting all graph data");
        
        try {
            pipelineService.deleteAllData();
            log.info("All graph data deleted successfully");
            
            return ResponseEntity.noContent().build();
            
        } catch (Exception e) {
            log.error("Error deleting data", e);
            throw new BadRequestException("Failed to delete data: " + e.getMessage());
        }
    }

    /**
     * Loads a smaller sample dataset for testing and development purposes.
     * 
     * @return Response with success message
     */
    @PostMapping("/sample")
    public ResponseEntity<ApiResponse<String>> loadSampleData() {
        log.info("Loading sample dataset");
        
        try {
            pipelineService.loadSampleData();
            log.info("Sample dataset loaded successfully");
            
            ApiResponse<String> response = ApiResponse.<String>builder()
                    .success(true)
                    .message("Sample dataset loaded successfully")
                    .data("Sample data has been inserted into the graph database")
                    .build();
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            log.error("Error loading sample data", e);
            throw new BadRequestException("Failed to load sample data: " + e.getMessage());
        }
    }

}
