package com.intuit.taxrefundstatus.refund.repo;

import com.intuit.taxrefundstatus.refund.model.RefundRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundRecordRepository extends JpaRepository {
    Optional<RefundRecord> findTopByUserIdOrderByTaxYearDesc(Long userId);
    Optional<RefundRecord> findByUserIdAndTaxYear(Long userId, Integer taxYear);
}
