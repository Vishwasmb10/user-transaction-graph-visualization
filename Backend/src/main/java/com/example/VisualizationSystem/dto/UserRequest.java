package com.example.VisualizationSystem.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.springframework.data.neo4j.core.schema.Id;

import java.time.LocalDateTime;

@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class UserRequest {

    @NotBlank
    private String userId;

    @NotBlank
    private String name;

    private String email;
    private String phone;
    private String address;
    private String paymentMethod;
    private LocalDateTime createdAt=LocalDateTime.now();

}
