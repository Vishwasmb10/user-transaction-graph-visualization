package com.example.VisualizationSystem.pipeline;

import com.example.VisualizationSystem.config.PipelineProperties;
import com.example.VisualizationSystem.generator.DataGeneratorService;
import com.example.VisualizationSystem.generator.Neo4jIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphPipelineService {

    private final PipelineProperties props;
    private final DataGeneratorService generator;
    private final Neo4jIngestionService ingestion;

    public void runPipeline() {

        Instant start = Instant.now();

        log.info("════════════════════════════════════════════════");
        log.info("  Neo4j Graph Pipeline — Starting");
        log.info("  Users: {}  |  Transactions: {}",
                props.getUserCount(), props.getTransactionCount());
        log.info("════════════════════════════════════════════════");

//        if (props.isCleanBeforeInsert()) {
//            timed("Phase 0 — Clean DB", ingestion::cleanDatabase);
//        }

        timed("Phase 1 — Create Schema", ingestion::createSchema);

        timed("Phase 2 — Generate Data", generator::generate);

        timed("Phase 3a — Insert Users",
                () -> ingestion.insertUsers(generator.getUsers()));
        timed("Phase 3b — Insert Transactions",
                () -> ingestion.insertTransactions(generator.getTransactions()));

        timed("Phase 3c — Insert PaymentMethod Nodes",
                ingestion::createPaymentMethodNodes);

        timed("Phase 4a — SENT / RECEIVED_BY edges",
                () -> ingestion.createParticipationEdges(generator.getTransactionEdges()));
        timed("Phase 4b — TRANSFERRED_TO edges",
                () -> ingestion.createTransferEdges(generator.getTransactionEdges()));

        timed("Phase 5a — Shared User attributes",
                ingestion::createSharedUserAttributeEdges);
        timed("Phase 5b — Shared Transaction attributes",
                ingestion::createSharedTransactionAttributeEdges);

        Duration total = Duration.between(start, Instant.now());

        log.info("════════════════════════════════════════════════");
        log.info("  Pipeline complete in {}", formatDuration(total));
        log.info("════════════════════════════════════════════════");
    }

    private void timed(String label, Runnable task) {
        log.info("┌── {} ────────────────────────", label);
        Instant t0 = Instant.now();
        task.run();
        Duration d = Duration.between(t0, Instant.now());
        log.info("└── {} done in {}", formatDuration(d));
        log.info("");
    }

    private String formatDuration(Duration d) {
        long mins = d.toMinutes();
        long secs = d.toSecondsPart();
        long ms = d.toMillisPart();
        if (mins > 0) return String.format("%dm %ds %dms", mins, secs, ms);
        if (secs > 0) return String.format("%ds %dms", secs, ms);
        return ms + "ms";
    }

    public void deleteAllData() {
        log.info("🧹 Deleting all graph data...");

        ingestion.cleanDatabase();

        log.info("✅ Graph data deleted.");
    }

    public void loadSampleData() {

        log.info("📦 Loading sample demo dataset...");

        ingestion.cleanDatabase();
        ingestion.createSchema();

        generator.generateSample();   // new method
        ingestion.insertUsers(generator.getUsers());
        ingestion.insertTransactions(generator.getTransactions());
        ingestion.createPaymentMethodNodes();
        ingestion.createParticipationEdges(generator.getTransactionEdges());
        ingestion.createTransferEdges(generator.getTransactionEdges());
        ingestion.createSharedUserAttributeEdges();
        ingestion.createSharedTransactionAttributeEdges();

        log.info("✅ Sample dataset loaded.");
    }

}
