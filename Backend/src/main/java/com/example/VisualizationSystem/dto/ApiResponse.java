package com.example.VisualizationSystem.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic API response wrapper.
 * Provides a consistent response structure across all endpoints.
 *
 * @param <T> The type of data being returned
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    
    /**
     * Indicates whether the operation was successful
     */
    private boolean success;
    
    /**
     * Human-readable message about the operation
     */
    private String message;
    
    /**
     * The actual data payload
     */
    private T data;
    
    /**
     * Optional metadata about the response
     */
    private Object metadata;
}
