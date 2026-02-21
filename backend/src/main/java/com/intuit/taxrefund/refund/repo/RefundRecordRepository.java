package com.intuit.taxrefund.refund.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.intuit.taxrefund.refund.model.RefundRecord;

import java.util.Optional;

public interface RefundRecordRepository extends JpaRepository<RefundRecord, Long> {
    Optional<RefundRecord> findTopByUserIdOrderByTaxYearDesc(Long userId);
    Optional<RefundRecord> findByUserIdAndTaxYear(Long userId, Integer taxYear);
}
