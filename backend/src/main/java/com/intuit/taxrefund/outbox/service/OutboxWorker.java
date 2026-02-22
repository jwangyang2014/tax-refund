package com.intuit.taxrefund.outbox.service;

import com.intuit.taxrefund.outbox.model.OutboxEvent;
import com.intuit.taxrefund.outbox.repo.OutboxEventRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboxWorker {

    private static final Logger log = LogManager.getLogger(OutboxWorker.class);

    private final OutboxEventRepository outboxRepo;
    private final OutboxWorkerTx tx;

    public OutboxWorker(OutboxEventRepository outboxRepo, OutboxWorkerTx tx) {
        this.outboxRepo = outboxRepo;
        this.tx = tx;
        log.info("outbox_worker_initialized");
    }

    @Scheduled(fixedDelayString = "PT5S")
    public void poll() {
        List<OutboxEvent> batch = outboxRepo.findUnprocessedWithAttemptsLessThan(20);
        if (batch.isEmpty()) return;

        log.info("outbox_batch_found size={}", batch.size());

        for (OutboxEvent evt : batch) {
            tx.processOne(evt.getId());
        }
    }
}