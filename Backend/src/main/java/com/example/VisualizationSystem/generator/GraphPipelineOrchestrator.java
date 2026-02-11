package com.example.VisualizationSystem.generator;

import com.example.VisualizationSystem.config.PipelineProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Runs the full pipeline on application startup.
 *
 * Phase 0 → Clean DB
 * Phase 1 → Schema (indexes/constraints)
 * Phase 2 → Generate data in memory
 * Phase 3 → Insert nodes (UNWIND batches)
 * Phase 4 → Direct edges (SENT, RECEIVED_BY, TRANSFERRED_TO)
 * Phase 5 → Shared-attribute edges (SAME_EMAIL, SAME_IP, etc.)
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class GraphPipelineOrchestrator implements CommandLineRunner {

    private final PipelineProperties    props;
    private final DataGeneratorService  generator;
    private final Neo4jIngestionService ingestion;

    @Override
    public void run(String... args) {
        Instant start = Instant.now();

        log.info("════════════════════════════════════════════════");
        log.info("  Neo4j Graph Pipeline — Starting");
        log.info("  Users: {}  |  Transactions: {}",
                props.getUserCount(), props.getTransactionCount());
        log.info("════════════════════════════════════════════════");

        // Phase 0
        if (props.isCleanBeforeInsert()) {
            timed("Phase 0 — Clean DB", ingestion::cleanDatabase);
        }

        // Phase 1
        timed("Phase 1 — Create Schema", ingestion::createSchema);

        // Phase 2
        timed("Phase 2 — Generate Data", generator::generate);

        // Phase 3
        timed("Phase 3a — Insert Users",
                () -> ingestion.insertUsers(generator.getUsers()));
        timed("Phase 3b — Insert Transactions",
                () -> ingestion.insertTransactions(generator.getTransactions()));

        // Phase 4
        timed("Phase 4a — SENT / RECEIVED_BY edges",
                () -> ingestion.createParticipationEdges(
                        generator.getTransactionEdges()));
        timed("Phase 4b — TRANSFERRED_TO edges",
                () -> ingestion.createTransferEdges(
                        generator.getTransactionEdges()));

        // Phase 5
        timed("Phase 5a — Shared User attributes",
                ingestion::createSharedUserAttributeEdges);
        timed("Phase 5b — Shared Transaction attributes",
                ingestion::createSharedTransactionAttributeEdges);

        // Done
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
        log.info("└── {} done in {}", label, formatDuration(d));
        log.info("");
    }

    private String formatDuration(Duration d) {
        long mins = d.toMinutes();
        long secs = d.toSecondsPart();
        long ms   = d.toMillisPart();
        if (mins > 0) return String.format("%dm %ds %dms", mins, secs, ms);
        if (secs > 0) return String.format("%ds %dms", secs, ms);
        return ms + "ms";
    }
}