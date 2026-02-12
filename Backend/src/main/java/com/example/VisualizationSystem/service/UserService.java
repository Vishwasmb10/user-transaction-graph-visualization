
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
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserGraphRelationshipRepository graphRepo;

    public UserResponse createOrUpdate(UserRequest request) {
        User existing = userRepository.findById(request.getUserId()).orElse(null);

        User saved = userRepository.upsertUser(
                request.getUserId(),
                request.getName(),
                request.getEmail(),
                request.getPhone(),
                request.getAddress(),
                request.getPaymentMethod()
        );

        if (existing == null || !Objects.equals(existing.getEmail(), saved.getEmail())) {
            graphRepo.deleteSameEmailLinks(saved.getUserId());
            if (saved.getEmail() != null) {
                graphRepo.linkUsersByEmail(saved.getUserId(), saved.getEmail());
            }
        }

        if (existing == null || !Objects.equals(existing.getPhone(), saved.getPhone())) {
            graphRepo.deleteSamePhoneLinks(saved.getUserId());
            if (saved.getPhone() != null) {
                graphRepo.linkUsersByPhone(saved.getUserId(), saved.getPhone());
            }
        }

        if (existing == null || !Objects.equals(existing.getAddress(), saved.getAddress())) {
            graphRepo.deleteSameAddressLinks(saved.getUserId());
            if (saved.getAddress() != null) {
                graphRepo.linkUsersByAddress(saved.getUserId(), saved.getAddress());
            }
        }

        if (existing == null || !Objects.equals(existing.getPaymentMethod(), saved.getPaymentMethod())) {
            graphRepo.deleteSamePaymentLinks(saved.getUserId());
            if (saved.getPaymentMethod() != null) {
                graphRepo.linkUsersByPaymentMethod(saved.getUserId(), saved.getPaymentMethod());
            }
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


