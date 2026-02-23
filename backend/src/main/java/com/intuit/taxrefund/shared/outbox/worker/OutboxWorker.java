package com.intuit.taxrefund.shared.outbox.worker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.List;

@Component
public class OutboxWorker {

    private static final Logger log = LogManager.getLogger(OutboxWorker.class);

    private final OutboxWorkerTx tx;
    private final OutboxClaimer claimer;
    private final String workerId;

    public OutboxWorker(OutboxWorkerTx tx, OutboxClaimer claimer) {
        this.tx = tx;
        this.claimer = claimer;
        this.workerId = buildWorkerId();
        log.info("outbox_worker_initialized workerId={}", workerId);
    }

    @Scheduled(fixedDelayString = "PT2S")
    public void poll() {
        // IMPORTANT: this call goes to another bean => @Transactional is applied
        List<Long> ids = claimer.claimBatchIds(workerId);
        if (ids.isEmpty()) return;

        log.info("outbox_batch_claimed workerId={} size={}", workerId, ids.size());

        for (Long id : ids) {
            tx.processOne(id, workerId);
        }
    }

    private static String buildWorkerId() {
        String jvmName = ManagementFactory.getRuntimeMXBean().getName(); // pid@host
        return "worker-" + jvmName;
    }
}