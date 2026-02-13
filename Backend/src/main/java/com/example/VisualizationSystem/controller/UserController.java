package com.example.VisualizationSystem.controller;

import com.example.VisualizationSystem.dto.PageResponse;
import com.example.VisualizationSystem.dto.UserRequest;
import com.example.VisualizationSystem.dto.UserResponse;
import com.example.VisualizationSystem.exception.BadRequestException;
import com.example.VisualizationSystem.exception.ResourceNotFoundException;
import com.example.VisualizationSystem.model.User;
import com.example.VisualizationSystem.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


/**
 * REST API controller for managing users.
 * Provides CRUD operations and filtering capabilities for users.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Validated
public class UserController {

    private final UserService userService;

    /**
     * Creates a new user or updates an existing one.
     * 
     * @param request User data
     * @return Created or updated user
     */
    @PostMapping
    public ResponseEntity<UserResponse> createOrUpdateUser( @RequestBody UserRequest request) {
        log.info("Creating/updating user: {}", request.getEmail());
        
        if (request == null) {
            throw new BadRequestException("User request cannot be null");
        }
        
        try {
            UserResponse user = userService.createOrUpdate(request);
            
            // Return 201 for creation, 200 for update
            HttpStatus status = request.getUserId() == null ? HttpStatus.CREATED : HttpStatus.OK;
            
            log.info("User {} successfully: {}", 
                    status == HttpStatus.CREATED ? "created" : "updated", 
                    user.getName());
            
            return ResponseEntity.status(status).body(user);
            
        } catch (Exception e) {
            log.error("Error creating/updating user", e);
            throw new BadRequestException("Failed to process user: " + e.getMessage());
        }
    }

    /**
     * Retrieves a paginated and filtered list of users.
     * Supports multiple filter criteria.
     * 
     * @param search General search term
     * @param email Email filter
     * @param phone Phone number filter
     * @param paymentMethod Payment method filter
     * @param page Page number (0-indexed)
     * @param size Page size
     * @return Paginated list of users
     */
    @GetMapping
    public ResponseEntity<PageResponse<User>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("Fetching users - page: {}, size: {}", page, size);
        
        try {
            PageResponse<User> response = userService.getUsersPaged(
                    sanitizeInput(search),
                    sanitizeInput(email),
                    sanitizeInput(phone),
                    sanitizeInput(paymentMethod),
                    page,
                    size
            );
            
            log.info("Successfully retrieved {} users", response.getContent().size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error fetching users", e);
            throw new BadRequestException("Failed to fetch users: " + e.getMessage());
        }
    }

    /**
     * Retrieves a specific user by ID.
     * 
     * @param id User ID
     * @return User details
     */
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable String id) {
        log.info("Fetching user with id: {}", id);
        
        if (id == null || id.trim().isEmpty()) {
            throw new BadRequestException("User ID cannot be empty");
        }
        
        User user = userService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        
        log.info("Successfully retrieved user: {}", id);
        return ResponseEntity.ok(user);
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
