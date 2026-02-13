package com.example.VisualizationSystem.service;

import com.example.VisualizationSystem.dto.PageResponse;
import com.example.VisualizationSystem.dto.UserRequest;
import com.example.VisualizationSystem.dto.UserResponse;
import com.example.VisualizationSystem.model.User;
import com.example.VisualizationSystem.repository.UserGraphRelationshipRepository;
import com.example.VisualizationSystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

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
                request.getPaymentMethods()
        );

        // ── email ──
        if (existing == null || !Objects.equals(existing.getEmail(), saved.getEmail())) {
            graphRepo.deleteSameEmailLinks(saved.getUserId());
            if (saved.getEmail() != null) {
                graphRepo.linkUsersByEmail(saved.getUserId(), saved.getEmail());
            }
        }

        // ── phone ──
        if (existing == null || !Objects.equals(existing.getPhone(), saved.getPhone())) {
            graphRepo.deleteSamePhoneLinks(saved.getUserId());
            if (saved.getPhone() != null) {
                graphRepo.linkUsersByPhone(saved.getUserId(), saved.getPhone());
            }
        }

        // ── address ──
        if (existing == null || !Objects.equals(existing.getAddress(), saved.getAddress())) {
            graphRepo.deleteSameAddressLinks(saved.getUserId());
            if (saved.getAddress() != null) {
                graphRepo.linkUsersByAddress(saved.getUserId(), saved.getAddress());
            }
        }

        // ── payment methods (hub-and-spoke) ──
        Set<String> oldMethods = existing != null && existing.getPaymentMethods() != null
                ? new HashSet<>(existing.getPaymentMethods())
                : Collections.emptySet();
        Set<String> newMethods = saved.getPaymentMethods() != null
                ? new HashSet<>(saved.getPaymentMethods())
                : Collections.emptySet();

        if (existing == null || !oldMethods.equals(newMethods)) {
            graphRepo.deletePaymentLinks(saved.getUserId());
            if (!newMethods.isEmpty()) {
                graphRepo.linkUserPaymentMethods(
                        saved.getUserId(), saved.getPaymentMethods());
            }
        }

        return UserResponse.builder()
                .name(saved.getName())
                .email(saved.getEmail())
                .phone(saved.getPhone())
                .address(saved.getAddress())
                .paymentMethods(saved.getPaymentMethods())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    public List<UserResponse> getAll() {
        return userRepository.findAllUserDtos();
    }

    public PageResponse<User> getUsersPaged(
            String search,
            String email,
            String phone,
            String paymentMethod,
            int page,
            int size
    ) {
        long total = userRepository.countUsers(search, email, phone, paymentMethod);
        long skip = (long) page * size;

        if (skip >= total && total > 0) {
            page = 0;
            skip = 0;
        }

        List<User> users = userRepository.findUsersPaged(
                search, email, phone, paymentMethod, skip, size);
        return new PageResponse<>(users, total, page, size);
    }

    public Optional<User> findById(String id) {
        return (userRepository.findById(id));
    }

//    public Optional<User> findByEmail(String email) {
//        return Optional.ofNullable(userRepository.findBy(email));
//    }

    public void deleteById(String id) {
        userRepository.deleteById(id);
    }

    public UserResponse partialUpdate(String id, UserRequest request) {
        return createOrUpdate(request); // Simple implementation
    }
}