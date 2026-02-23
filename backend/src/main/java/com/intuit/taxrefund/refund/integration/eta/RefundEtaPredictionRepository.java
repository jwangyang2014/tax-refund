package com.intuit.taxrefund.refund.integration.eta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundEtaPredictionRepository extends JpaRepository<RefundEtaPrediction, Long> {

    Optional<RefundEtaPrediction> findTopByUserIdAndTaxYearAndStatusOrderByCreatedAtDesc(
        Long userId, int taxYear, String status
    );
    boolean existsByUserIdAndTaxYearAndStatusAndModelVersion(Long userId, int taxYear, String status, String modelVersion);
}