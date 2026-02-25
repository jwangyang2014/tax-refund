package com.intuit.taxrefund.assistant.infra;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class AssistantQuotaService {
    private static final ZoneId QUOTA_ZONE = ZoneId.of("America/Los_Angeles");

    private final StringRedisTemplate redis;

    public AssistantQuotaService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean tryConsumeDaily(long userId, int dailyLimit) {
        String day = LocalDate.now(QUOTA_ZONE).format(DateTimeFormatter.BASIC_ISO_DATE); // YYYYMMDD
        String key = "llm:quota:" + day + ":u:" + userId;

        Long n = redis.opsForValue().increment(key);
        if (n != null && n == 1L) {
            // Keep a bit longer than a day to survive timezone/day rollover safely
            redis.expire(key, Duration.ofHours(26));
        }

        return n != null && n <= dailyLimit;
    }
}