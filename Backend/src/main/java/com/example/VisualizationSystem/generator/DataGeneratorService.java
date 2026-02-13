package com.example.VisualizationSystem.generator;

import com.example.VisualizationSystem.config.PipelineProperties;
import com.example.VisualizationSystem.dto.TransactionEdgeData;
import com.example.VisualizationSystem.model.Transaction;
import com.example.VisualizationSystem.model.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataGeneratorService {

    private final PipelineProperties props;

    @Getter
    private List<User> users;

    @Getter
    private List<Transaction> transactions;

    @Getter
    private List<TransactionEdgeData> transactionEdges;

    // Fixed pools
    private static final List<String> PAYMENT_METHOD_TYPES = List.of(
            "CREDIT_CARD", "DEBIT_CARD", "CASH",
            "BANK_TRANSFER", "UPI", "PAYPAL", "CRYPTO"
    );

    private static final List<String> CURRENCIES = List.of(
            "USD", "USD", "USD", "USD",   // weighted toward USD
            "EUR", "EUR",
            "GBP",
            "INR"
    );

    private static final List<String> STATUSES = List.of(
            "COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED",  // 50%
            "COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED",
            "PENDING", "PENDING",                                              // 10%
            "FAILED",                                                          // 5%
            "FLAGGED",                                                         // 5%
            "REVERSED"                                                         // 5%
    );

    public void generate() {
        log.info("Generating {} users and {} transactions...",
                props.getUserCount(), props.getTransactionCount());

        Faker faker = new Faker();
        Random rng = new Random(42);

        // ── Build attribute pools for controlled overlap ───────
        List<String> emailPool   = buildPool(props.getEmailPoolSize(),
                () -> faker.internet().emailAddress());
        List<String> phonePool   = buildPool(props.getPhonePoolSize(),
                () -> faker.phoneNumber().subscriberNumber(10));
        List<String> addressPool = buildPool(props.getAddressPoolSize(),
                () -> faker.address().streetAddress());
        List<String> ipPool      = buildPool(props.getIpPoolSize(),
                () -> faker.internet().ipV4Address());
        List<String> devicePool  = buildPool(props.getDevicePoolSize(),
                () -> "DEV-" + faker.number().digits(12));

        // ── Generate Users ─────────────────────────────────────
        users = new ArrayList<>(props.getUserCount());
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < props.getUserCount(); i++) {
            int methodCount = 1 + rng.nextInt(3);
            List<String> methods = pickMultiple(PAYMENT_METHOD_TYPES, methodCount, rng);

            users.add(User.builder()
                    .userId("U-" + UUID.randomUUID())
                    .name(faker.name().fullName())
                    .email(pick(emailPool, rng))
                    .phone(pick(phonePool, rng))
                    .address(pick(addressPool, rng))
                    .paymentMethods(methods)
                    .createdAt(now.minusDays(rng.nextInt(365)))
                    .build());
        }
        log.info("  ✓ {} users generated", users.size());

        // ── Generate Transactions + edge metadata ──────────────
        transactions     = new ArrayList<>(props.getTransactionCount());
        transactionEdges = new ArrayList<>(props.getTransactionCount());

        for (int i = 0; i < props.getTransactionCount(); i++) {
            User sender = users.get(rng.nextInt(users.size()));
            User receiver = users.get(rng.nextInt(users.size()));
            while (receiver.getUserId().equals(sender.getUserId())) {
                receiver = users.get(rng.nextInt(users.size()));
            }

            // Pick a payment method the sender actually has
            String txPaymentMethod = pick(sender.getPaymentMethods(), rng);

            // High-value transactions are more likely to be flagged
            double amount = Math.round(rng.nextDouble() * 50_000.0 * 100.0) / 100.0;
            String status = pick(STATUSES, rng);
            if (amount > 10_000 && rng.nextDouble() < 0.3) {
                status = "FLAGGED";
            }
            if (amount > 25_000 && rng.nextDouble() < 0.15) {
                status = "FAILED";
            }

            Transaction txn = Transaction.builder()
                    .transactionId("TX-" + UUID.randomUUID())
                    .amount(amount)
                    .currency(pick(CURRENCIES, rng))
                    .timestamp(now.minus(
                            ThreadLocalRandom.current().nextLong(365 * 24 * 60),
                            ChronoUnit.MINUTES))
                    .ip(pick(ipPool, rng))
                    .deviceId(pick(devicePool, rng))
                    .status(status)
                    .paymentMethod(txPaymentMethod)
                    .build();

            transactions.add(txn);
            transactionEdges.add(TransactionEdgeData.builder()
                    .senderId(sender.getUserId())
                    .receiverId(receiver.getUserId())
                    .transaction(txn)
                    .build());
        }

        // Log distribution stats
        Map<String, Long> statusCounts = new HashMap<>();
        Map<String, Long> currencyCounts = new HashMap<>();
        Map<String, Long> pmCounts = new HashMap<>();
        for (Transaction t : transactions) {
            statusCounts.merge(t.getStatus(), 1L, Long::sum);
            currencyCounts.merge(t.getCurrency(), 1L, Long::sum);
            pmCounts.merge(t.getPaymentMethod(), 1L, Long::sum);
        }
        log.info("  ✓ {} transactions generated", transactions.size());
        log.info("    Status distribution:  {}", statusCounts);
        log.info("    Currency distribution: {}", currencyCounts);
        log.info("    Payment distribution:  {}", pmCounts);
    }

    // ── Helpers ────────────────────────────────────────────────

    private List<String> buildPool(int size, Supplier<String> gen) {
        Set<String> set = new LinkedHashSet<>();
        while (set.size() < size) {
            set.add(gen.get());
        }
        return new ArrayList<>(set);
    }

    private static <T> T pick(List<T> pool, Random rng) {
        return pool.get(rng.nextInt(pool.size()));
    }

    private static <T> List<T> pickMultiple(List<T> pool, int count, Random rng) {
        List<T> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, rng);
        return new ArrayList<>(shuffled.subList(0, Math.min(count, shuffled.size())));
    }
}