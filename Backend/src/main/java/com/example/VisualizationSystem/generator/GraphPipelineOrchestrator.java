package com.example.VisualizationSystem.generator;

import com.example.VisualizationSystem.config.PipelineProperties;
import com.example.VisualizationSystem.pipeline.GraphPipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@Order(1)
@RequiredArgsConstructor
public class GraphPipelineOrchestrator implements CommandLineRunner {

    private final PipelineProperties props;
    private final GraphPipelineService pipelineService;

    @Override
    public void run(String... args) {
        if (props.isRunOnStartup()) {
            pipelineService.runPipeline();
        }
    }
}
