package com.intuit.taxrefund.refund.repository;

import com.intuit.taxrefund.refund.model.RefundAccessAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundAccessAuditRepository extends JpaRepository<RefundAccessAudit, Long> {}