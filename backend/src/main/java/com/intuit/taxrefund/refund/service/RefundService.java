package com.intuit.taxrefund.refund.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.taxrefund.ai.AiConfig;
import com.intuit.taxrefund.ai.model.AiRequestLog;
import com.intuit.taxrefund.ai.repo.AiRequestLogRepository;
import com.intuit.taxrefund.ai.service.AiClient;
import com.intuit.taxrefund.ai.service.AiClientRouter;
import com.intuit.taxrefund.auth.jwt.JwtService;
import com.intuit.taxrefund.auth.model.AppUser;
import com.intuit.taxrefund.auth.repo.UserRepository;
import com.intuit.taxrefund.refund.api.dto.RefundStatusResponse;
import com.intuit.taxrefund.refund.model.RefundRecord;
import com.intuit.taxrefund.refund.model.RefundStatus;
import com.intuit.taxrefund.refund.repo.RefundRecordRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class RefundService {
    private static final long SECONDS_IN_DAY = 86400L;

    private final RefundRecordRepository refundRepo;
    private final UserRepository userRepo;
    private final IrsAdapter irs;
    private final AiRequestLogRepository aiLogRepo;
    private final AiConfig aiConfig;
    private final AiClient aiClient;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RefundService(
        RefundRecordRepository refundRepo,
        UserRepository userRepo,
        IrsAdapter irs,
        AiClientRouter aiClientRouter,
        AiRequestLogRepository aiLogRepo,
        AiConfig aiConfig,
        StringRedisTemplate redis,
        ObjectMapper objectMapper
    ) {
        this.refundRepo = refundRepo;
        this.userRepo = userRepo;
        this.irs = irs;
        this.aiLogRepo = aiLogRepo;
        this.aiConfig = aiConfig;

        this.aiClient = aiClientRouter.getClient();
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RefundStatusResponse getLatestRefundStatus(JwtService.JwtPrincipal principal) {
        Long userId = principal.userId();

        String cacheKey = "refund:latest:" + userId;
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, RefundStatusResponse.class);
            } catch (Exception ignore) {
                // cache corruption / schema change -> ignore cache
            }
        }

        IrsAdapter.IrsRefundResult irsResult = irs.fetchMostRecentRefund(userId);
        RefundRecord record = refundRepo.findByUserIdAndTaxYear(userId, irsResult.taxYear())
            .orElseGet(() -> {
                AppUser user = userRepo.findById(userId).orElseThrow();
                return new RefundRecord(user, irsResult.taxYear(), RefundStatus.RECEIVED);
            });

        record.updateFromIrs(irsResult.status(), irsResult.expectedAmount(), irsResult.trackingId());

        // If not AVAILABLE, ask AI for ETA and store estimated date
        Instant estimatedAvailableAt = record.getAvailableAtEstimated();
        String aiExplanation = null;

        RefundStatus status  = record.getStatus();
        if (status != RefundStatus.AVAILABLE && status != RefundStatus.REJECTED) {
            String input = "status=" + status + ", expectedAmount=" + record.getExpectedAmount();
            AppUser user = record.getUser();
            try {
                AiClient.PredictEtaResult aiEta = aiClient.predictRefundEtaDays(status, record.getExpectedAmount());
                estimatedAvailableAt = Instant.now().plusSeconds(aiEta.etaDays() * SECONDS_IN_DAY);
                aiExplanation = aiEta.explanation();

                logAiRequest(user, input, true,
                    "{\"etaDays\": " + aiEta.etaDays() + ", \"explanation\": \"" + safe(aiEta.explanation()) + "\"}"
                );
            } catch (Exception e) {
                logAiRequest(user, input, false, e.getMessage());
            }

            record.setAvailableAtEstimated(estimatedAvailableAt);
        }

        refundRepo.save(record);

        RefundStatusResponse resp = new RefundStatusResponse(
            record.getTaxYear(),
            record.getStatus().name(),
            record.getLastUpdatedAt(),
            record.getExpectedAmount(),
            record.getIrsTrackingId(),
            record.getAvailableAtEstimated(),
            aiExplanation
        );

        // Cache the result
        try {
            redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(resp), Duration.ofSeconds(60));
        } catch (Exception ignore) {
            // if Redis/json fails, still return response
        }

        return resp;
    }

    private void logAiRequest(AppUser user, String input, boolean success, String output) {
        aiLogRepo.save(AiRequestLog.log(
            user,
            "REFUND_ETA",
            input,
            aiConfig.getProvider(),
            aiClient.getModel(),
            output,
            success
        ));
    }

    private static String safe(String s) {
        if (s == null) return "null";
        return s.replace("\"", "'");
    }
}
