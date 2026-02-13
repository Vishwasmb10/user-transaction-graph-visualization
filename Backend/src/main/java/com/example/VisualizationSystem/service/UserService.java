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
                request.getPaymentMethods()        // ✅ List<String>
        );

        // ── email (unchanged) ──
        if (existing == null || !Objects.equals(existing.getEmail(), saved.getEmail())) {
            graphRepo.deleteSameEmailLinks(saved.getUserId());
            if (saved.getEmail() != null) {
                graphRepo.linkUsersByEmail(saved.getUserId(), saved.getEmail());
            }
        }

        // ── phone (unchanged) ──
        if (existing == null || !Objects.equals(existing.getPhone(), saved.getPhone())) {
            graphRepo.deleteSamePhoneLinks(saved.getUserId());
            if (saved.getPhone() != null) {
                graphRepo.linkUsersByPhone(saved.getUserId(), saved.getPhone());
            }
        }

        // ── address (unchanged) ──
        if (existing == null || !Objects.equals(existing.getAddress(), saved.getAddress())) {
            graphRepo.deleteSameAddressLinks(saved.getUserId());
            if (saved.getAddress() != null) {
                graphRepo.linkUsersByAddress(saved.getUserId(), saved.getAddress());
            }
        }

        // ✅ CHANGED: hub-and-spoke payment method linking
        Set<String> oldMethods = existing != null && existing.getPaymentMethods() != null
                ? new HashSet<>(existing.getPaymentMethods())
                : Collections.emptySet();
        Set<String> newMethods = saved.getPaymentMethods() != null
                ? new HashSet<>(saved.getPaymentMethods())
                : Collections.emptySet();

        if (existing == null || !oldMethods.equals(newMethods)) {
            graphRepo.deletePaymentLinks(saved.getUserId());      // ✅ delete USES_PAYMENT
            if (!newMethods.isEmpty()) {
                graphRepo.linkUserPaymentMethods(                  // ✅ create USES_PAYMENT
                        saved.getUserId(), saved.getPaymentMethods());
            }
        }

        return UserResponse.builder()
                .name(saved.getName())
                .email(saved.getEmail())
                .phone(saved.getPhone())
                .address(saved.getAddress())
                .paymentMethods(saved.getPaymentMethods())         // ✅
                .createdAt(saved.getCreatedAt())
                .build();
    }

    public List<UserResponse> getAll() {
        return userRepository.findAllUserDtos();
    }

    public PageResponse<User> getUsersPaged(
            String email,
            String phone,
            String paymentMethod,       // ✅ optional filter
            int page,
            int size
    ) {
        long total = userRepository.countUsers(email, phone, paymentMethod);
        long skip = (long) page * size;

        if (skip >= total && total > 0) {
            page = 0;
            skip = 0;
        }

        List<User> users = userRepository.findUsersPaged(
                email, phone, paymentMethod, skip, size);
        return new PageResponse<>(users, total, page, size);
    }
}