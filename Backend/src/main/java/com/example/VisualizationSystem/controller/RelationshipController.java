package com.example.VisualizationSystem.controller;

import com.example.VisualizationSystem.service.UserRelationshipService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/relationships")
@RequiredArgsConstructor
public class RelationshipController {

    private final UserRelationshipService relationshipService;

    @GetMapping("/user/{id}")
    public Map<String, Object> getUserConnections(@PathVariable String id) {
        return relationshipService.getUserGraph(id);
    }

}
