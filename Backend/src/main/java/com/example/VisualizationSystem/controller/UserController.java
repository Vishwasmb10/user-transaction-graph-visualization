package com.example.VisualizationSystem.controller;

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
    public UserResponse createOrUpdate(@RequestBody UserRequest userRequest){
        return userService.createOrUpdate(userRequest);
    }

    @GetMapping
    public List<UserResponse> getAll(){
        return userService.getAll();
    }


}
