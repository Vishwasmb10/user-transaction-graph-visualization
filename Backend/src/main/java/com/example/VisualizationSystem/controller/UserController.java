package com.example.VisualizationSystem.controller;

import com.example.VisualizationSystem.dto.PageResponse;
import com.example.VisualizationSystem.dto.UserRequest;
import com.example.VisualizationSystem.dto.UserResponse;
import com.example.VisualizationSystem.model.User;
import com.example.VisualizationSystem.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    @PostMapping
    public UserResponse createOrUpdate(@RequestBody UserRequest userRequest) {
        return userService.createOrUpdate(userRequest);
    }

//    @GetMapping
//    public List<UserResponse> getAll() {
//        return userService.getAll();
//    }

    @GetMapping
    public PageResponse<User> getUsers(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {

        // 🔥 If any filter is applied → reset page to 0
        if (email != null || phone != null) {
            page = 0;
        }

        return userService.getUsersPaged(email, phone, page, size);
    }


}
