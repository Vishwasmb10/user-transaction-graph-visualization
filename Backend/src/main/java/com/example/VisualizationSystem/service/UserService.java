
package com.example.VisualizationSystem.service;

import com.example.VisualizationSystem.dto.PageResponse;
import com.example.VisualizationSystem.dto.UserRequest;
import com.example.VisualizationSystem.dto.UserRequest;
import com.example.VisualizationSystem.dto.UserResponse;
import com.example.VisualizationSystem.model.User;
import com.example.VisualizationSystem.repository.UserGraphRelationshipRepository;
import com.example.VisualizationSystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserGraphRelationshipRepository graphRepo;

    public UserResponse createOrUpdate(UserRequest request) {

        User user = User.builder()
                .userId(request.getUserId())
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .paymentMethod(request.getPaymentMethod())
                .createdAt(LocalDateTime.now())
                .build();

        User saved = userRepository.upsertUser(
                request.getUserId(),
                request.getName(),
                request.getEmail(),
                request.getPhone(),
                request.getAddress(),
                request.getPaymentMethod()
        );


        // Relationship detection (shared attributes)
        if (saved.getEmail() != null) {
            graphRepo.linkUsersByEmail(saved.getUserId(), saved.getEmail());
        }

        if (saved.getPhone() != null) {
            graphRepo.linkUsersByPhone(saved.getUserId(), saved.getPhone());
        }

        if (saved.getAddress() != null) {
            graphRepo.linkUsersByAddress(saved.getUserId(), saved.getAddress());
        }

        if (saved.getPaymentMethod() != null) {
            graphRepo.linkUsersByPaymentMethod(saved.getUserId(), saved.getPaymentMethod());
        }

        return UserResponse
                .builder()
                .name(saved.getName())
                .email(saved.getEmail())
                .phone(saved.getPhone())
                .address(saved.getAddress())
                .paymentMethod(saved.getPaymentMethod())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    public List<UserResponse> getAll() {
        return userRepository.findAllUserDtos();
    }

    public PageResponse<User> getUsersPaged(
            String email,
            String phone,
            int page,
            int size
    ) {
        long total = userRepository.countUsers(email, phone);

        long skip = (long) page * size;

        // 🔥 Clamp invalid page
        if (skip >= total && total > 0) {
            page = 0;
            skip = 0;
        }

        List<User> users = userRepository.findUsersPaged(email, phone, skip, size);

        return new PageResponse<>(users, total, page, size);
    }


}


