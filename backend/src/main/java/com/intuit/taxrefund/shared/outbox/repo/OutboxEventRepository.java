package com.intuit.taxrefund.shared.outbox.repo;

import com.intuit.taxrefund.shared.outbox.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query("""
        select e from OutboxEvent e
        where e.processedAt is null
        order by e.createdAt asc
    """)
    List<OutboxEvent> findUnprocessed();

    @Query("""
        select e from OutboxEvent e
        where e.processedAt is null and e.attempts < :maxAttempts
        order by e.createdAt asc
    """)
    List<OutboxEvent> findUnprocessedWithAttemptsLessThan(int maxAttempts);

    @Query(value = """
        select * from outbox_event
        where processed_at is null
          and attempts < :maxAttempts
          and (locked_at is null or locked_at < :lockExpiry)
        order by created_at asc
        limit :limit
        for update skip locked
        """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(int limit, int maxAttempts, Instant lockExpiry);

    @Modifying
    @Transactional
    @Query(value = """
        update outbox_event
        set locked_at = now(), locked_by = :workerId
        where id in (:ids)
        """, nativeQuery = true)
    int markLocked(List<Long> ids, String workerId);

    @Modifying
    @Transactional
    @Query(value = """
        update outbox_event
        set locked_at = null, locked_by = null
        where id = :id
        """, nativeQuery = true)
    int unlock(long id);
}