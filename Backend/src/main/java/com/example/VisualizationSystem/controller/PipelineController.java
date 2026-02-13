package com.example.VisualizationSystem.controller;

import com.example.VisualizationSystem.pipeline.GraphPipelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final GraphPipelineService pipelineService;

    @PostMapping("/run")
    public ResponseEntity<String> runPipeline() {

        pipelineService.runPipeline();

        return ResponseEntity.ok("100k transaction nodes and 10k users with relation mapping inserted Successfully");
    }

    @DeleteMapping("/data")
    public ResponseEntity<String> deleteData() {

        pipelineService.deleteAllData();

        return ResponseEntity.ok("All graph data deleted successfully.");
    }

    @PostMapping("/sample")
    public ResponseEntity<String> loadSample() {

        pipelineService.loadSampleData();

        return ResponseEntity.ok("Sample dataset loaded successfully.");
    }


}
