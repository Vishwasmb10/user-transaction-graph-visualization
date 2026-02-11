package com.example.VisualizationSystem.controller;

import com.example.VisualizationSystem.service.TransactionRelationshipService;
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

    private final UserRelationshipService userRelationshipService;
    private final TransactionRelationshipService transactionRelationshipService;

    @GetMapping("/user/{id}")
    public Map<String, Object> getUserConnections(@PathVariable String id) {
        return userRelationshipService.getUserGraph(id);
    }

    @GetMapping("/transaction/{id}")
    public Map<String,Object> getTransactionConnections(@PathVariable("id") String id) {
        return transactionRelationshipService.getTransactionGraph(id);
    }

}
