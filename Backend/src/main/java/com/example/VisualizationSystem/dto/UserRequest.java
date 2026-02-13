package com.example.VisualizationSystem.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.springframework.data.neo4j.core.schema.Id;

import java.time.LocalDateTime;
import java.util.List;

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
    private List<String> paymentMethods;
    private LocalDateTime createdAt=LocalDateTime.now();

}
