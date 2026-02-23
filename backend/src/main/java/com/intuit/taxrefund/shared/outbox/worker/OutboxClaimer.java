package com.intuit.taxrefund.shared.outbox.worker;

import com.intuit.taxrefund.shared.outbox.model.OutboxEvent;
import com.intuit.taxrefund.shared.outbox.repo.OutboxEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class OutboxClaimer {

    private static final int BATCH_SIZE = 25;
    private static final int MAX_ATTEMPTS = 20;
    private static final Duration LEASE = Duration.ofMinutes(5);

    private final OutboxEventRepository outboxRepo;

    public OutboxClaimer(OutboxEventRepository outboxRepo) {
        this.outboxRepo = outboxRepo;
    }

    @Transactional
    public List<Long> claimBatchIds(String workerId) {
        Instant lockExpiry = Instant.now().minus(LEASE);

        List<OutboxEvent> lockedRows = outboxRepo.lockNextBatch(BATCH_SIZE, MAX_ATTEMPTS, lockExpiry);
        if (lockedRows.isEmpty()) return List.of();

        List<Long> ids = lockedRows.stream().map(OutboxEvent::getId).toList();

        // MUST run inside a transaction
        outboxRepo.markLocked(ids, workerId);

        return ids;
    }
}