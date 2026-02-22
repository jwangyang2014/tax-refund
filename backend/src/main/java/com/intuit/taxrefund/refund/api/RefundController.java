package com.intuit.taxrefund.refund.api;

import com.intuit.taxrefund.auth.jwt.JwtService;
import com.intuit.taxrefund.refund.api.dto.RefundStatusInternalUpdateRequest;
import com.intuit.taxrefund.refund.api.dto.RefundStatusResponse;
import com.intuit.taxrefund.refund.service.IrsAdapter;
import com.intuit.taxrefund.refund.service.MockIrsAdapter;
import com.intuit.taxrefund.refund.service.RefundService;
import jakarta.validation.Valid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/refund")
public class RefundController {
    private static final Logger log = LogManager.getLogger(RefundController.class);

    private final RefundService refundService;
    private final MockIrsAdapter mockIrs;
    private final StringRedisTemplate redis;

    public RefundController(RefundService refundService, MockIrsAdapter mockIrs, StringRedisTemplate redis) {
        this.refundService = refundService;
        this.mockIrs = mockIrs;
        this.redis = redis;
    }

    @GetMapping("/latest")
    public RefundStatusResponse latest(Authentication auth) {
        JwtService.JwtPrincipal principal = (JwtService.JwtPrincipal) auth.getPrincipal();
        RefundStatusResponse resp = refundService.getLatestRefundStatus(principal);
        log.info("refund_latest_served userId={} taxYear={} status={}",
            principal.userId(), resp.taxYear(), resp.status());
        return resp;
    }

    @PostMapping("/simulate")
    public void simulate(Authentication auth, @Valid @RequestBody RefundStatusInternalUpdateRequest req) {
        JwtService.JwtPrincipal principal = (JwtService.JwtPrincipal) auth.getPrincipal();

        mockIrs.upsert(
            principal.userId(),
            new IrsAdapter.IrsRefundResult(
                req.taxYear(), req.statusEnum(), req.expectedAmount(), req.trackingId()
            )
        );

        try {
            redis.delete("refund:latest:" + principal.userId());
        } catch (Exception e) {
            log.warn("refund_simulate_cache_invalidate_failed userId={} err={}", principal.userId(), e.toString());
        }

        log.info("refund_simulated userId={} taxYear={} status={}",
            principal.userId(), req.taxYear(), req.statusEnum());
    }
}