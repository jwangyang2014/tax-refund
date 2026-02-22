package com.intuit.taxrefund.outbox.service;

import com.intuit.taxrefund.outbox.model.OutboxEvent;
import com.intuit.taxrefund.outbox.repo.OutboxEventRepository;
import jakarta.transaction.Transactional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class OutboxWorkerTx {

    private static final Logger log = LogManager.getLogger(OutboxWorkerTx.class);

    private final OutboxEventRepository outboxRepo;
    private final OutboxEventHandler handler;

    public OutboxWorkerTx(OutboxEventRepository outboxRepo, OutboxEventHandler handler) {
        this.outboxRepo = outboxRepo;
        this.handler = handler;
    }

    @Transactional
    public void processOne(Long outboxEventId) {
        OutboxEvent evt = outboxRepo.findById(outboxEventId).orElse(null);
        if (evt == null) return;
        if (evt.getProcessedAt() != null) return;

        try {
            handler.handle(evt);
            evt.markProcessed();
            log.info("outbox_processed id={} type={} key={}", evt.getId(), evt.getEventType(), evt.getAggregateKey());
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();

            if (msg.contains("already exists") || msg.contains("unique constraint") || msg.contains("duplicate key")) {
                evt.bumpAttempt(msg);
                evt.markProcessed();
                log.info("outbox_idempotent_success id={} reason={}", evt.getId(), msg);
            } else if (msg.contains("Model not trained yet")) {
                evt.bumpAttempt(msg);
                evt.markProcessed();
                log.warn("outbox_model_not_ready id={} reason={}", evt.getId(), msg);
            } else {
                evt.bumpAttempt(msg);
                log.error("outbox_failed id={} attempts={} err={}", evt.getId(), evt.getAttempts(), msg);
            }
        }

        outboxRepo.save(evt);
    }
}