package com.intuit.taxrefund.assistant.infra;

import com.intuit.taxrefund.assistant.model.ConversationState;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class ConversationStateStore {
    private final StringRedisTemplate redis;

    public ConversationStateStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public ConversationState get(long userId) {
        String key = key(userId);
        return Optional.ofNullable(redis.opsForValue().get(key))
            .map(ConversationState::valueOf)
            .orElse(ConversationState.START);
    }

    public void set(long userId, ConversationState state) {
        redis.opsForValue().set(key(userId), state.name(), Duration.ofHours(1));
    }

    private static String key(long userId) {
        return "chat:state:" + userId;
    }
}