package com.intuit.taxrefund.assistant.core;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class AssistantQuotaService {

    private final StringRedisTemplate redis;

    public AssistantQuotaService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean tryConsumeDaily(long userId, int dailyLimit) {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // YYYYMMDD
        String key = "llm:quota:" + day + ":u:" + userId;

        Long n = redis.opsForValue().increment(key);
        if (n != null && n == 1L) {
            // expire a little after day ends
            redis.expire(key, Duration.ofHours(26));
        }

        return n != null && n <= dailyLimit;
    }
}