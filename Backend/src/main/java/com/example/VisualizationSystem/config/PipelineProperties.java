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

    // ═══════════════════════════════════════════════════════════════
    // DATA GENERATION
    // ═══════════════════════════════════════════════════════════════
    private int userCount          = 10_000;
    private int transactionCount   = 100_000;

    // ═══════════════════════════════════════════════════════════════
    // BATCH SIZES
    // ═══════════════════════════════════════════════════════════════
    private int nodeBatchSize          = 2_000;
    private int relationshipBatchSize  = 5_000;

    // ═══════════════════════════════════════════════════════════════
    // ATTRIBUTE POOL SIZES (larger = less sharing = fewer edges)
    // ═══════════════════════════════════════════════════════════════
    private int emailPoolSize    = 7_000;
    private int phonePoolSize    = 8_000;
    private int addressPoolSize  = 500;
    private int paymentPoolSize  = 200;
    private int ipPoolSize       = 20_000;
    private int devicePoolSize   = 10_000;

    // ═══════════════════════════════════════════════════════════════
    // RELATIONSHIP SAMPLING CONFIGURATION
    // Controls what percentage of potential edges are actually created
    // ═══════════════════════════════════════════════════════════════

    // User attribute sampling (0.0 to 1.0)
    private double sameEmailSampleRate   = 1.0;    // 100% - these are rare/important
    private double samePhoneSampleRate   = 1.0;    // 100% - these are rare/important
    private double sameAddressSampleRate = 0.15;   // 15% - addresses are shared more

    // Transaction attribute sampling (0.0 to 1.0)
    private double sameIpSampleRate      = 0.02;   // 2% - IPs are heavily shared
    private double sameDeviceSampleRate  = 0.02;   // 2% - devices are heavily shared

    // ═══════════════════════════════════════════════════════════════
    // CLUSTER SIZE LIMITS
    // Skip creating edges for clusters larger than this
    // (prevents massive edge explosion from popular IPs/devices)
    // ═══════════════════════════════════════════════════════════════
    private int maxEmailCluster   = 50;   // Allow larger email clusters
    private int maxPhoneCluster   = 50;   // Allow larger phone clusters
    private int maxAddressCluster = 20;   // Moderate limit for addresses
    private int maxIpCluster      = 10;   // Strict limit for IPs
    private int maxDeviceCluster  = 10;   // Strict limit for devices

    // ═══════════════════════════════════════════════════════════════
    // RELATIONSHIP TOGGLES
    // Completely enable/disable specific relationship types
    // ═══════════════════════════════════════════════════════════════
    private boolean createSameEmail      = true;
    private boolean createSamePhone      = true;
    private boolean createSameAddress    = true;
    private boolean createSameIp         = true;
    private boolean createSameDevice     = true;
    private boolean createTransferEdges  = true;
    private boolean createUsesPayment    = true;

    // ═══════════════════════════════════════════════════════════════
    // PIPELINE CONTROL
    // ═══════════════════════════════════════════════════════════════
    private boolean cleanBeforeInsert = true;
    private boolean runOnStartup      = false;
}