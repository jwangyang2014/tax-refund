package com.intuit.taxrefundstatus.ai.repo;

import com.intuit.taxrefundstatus.ai.model.AiRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiRequestLogRepository extends JpaRepository {
    Optional<AiRequestLog> findTopByUserIdOrderByCreatedAtDesc(Long userId);
}
