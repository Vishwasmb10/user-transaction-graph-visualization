package com.example.VisualizationSystem.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "pipeline")
public class PipelineProperties {

    private int userCount          = 10_000;
    private int transactionCount   = 100_000;

    private int nodeBatchSize          = 2_000;
    private int relationshipBatchSize  = 5_000;

    private int emailPoolSize    = 7_000;
    private int phonePoolSize    = 8_000;
    private int addressPoolSize  = 500;
    private int paymentPoolSize  = 200;
    private int ipPoolSize       = 20_000;
    private int devicePoolSize   = 10_000;

    private int maxPairwiseCluster = 50;
    private boolean cleanBeforeInsert = true;
    private boolean runOnStartup=false;


}