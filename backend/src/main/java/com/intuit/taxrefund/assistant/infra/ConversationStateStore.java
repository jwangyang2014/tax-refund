package com.intuit.taxrefund.assistant.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.taxrefund.assistant.model.ConversationContext;
import com.intuit.taxrefund.assistant.model.ConversationState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Persists a full {@link ConversationContext} in Redis per user.
 *
 * Key  : "chat:ctx:{userId}"
 * Value: JSON-serialised ConversationContext
 * TTL  : 1 hour (reset on every write)
 *
 * Falls back to {@link ConversationContext#start()} on any read error so a
 * corrupt or expired Redis key never crashes the assistant flow.
 */
@Component
public class ConversationStateStore {

    private static final Logger   log = LogManager.getLogger(ConversationStateStore.class);
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final ObjectMapper        om;

    public ConversationStateStore(StringRedisTemplate redis, ObjectMapper om) {
        this.redis = redis;
        this.om    = om;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Load the full context for a user.
     * Returns {@link ConversationContext#start()} when no context exists or on error.
     */
    public ConversationContext get(long userId) {
        try {
            String json = redis.opsForValue().get(contextKey(userId));
            if (json == null || json.isBlank()) {
                return ConversationContext.start();
            }
            return om.readValue(json, ConversationContext.class);
        } catch (Exception e) {
            log.warn("conversation_ctx_read_failed userId={} err={} – starting fresh", userId, e.toString());
            return ConversationContext.start();
        }
    }

    /**
     * Persist an updated context, resetting the TTL.
     */
    public void set(long userId, ConversationContext ctx) {
        try {
            String json = om.writeValueAsString(ctx);
            redis.opsForValue().set(contextKey(userId), json, TTL);
        } catch (Exception e) {
            log.error("conversation_ctx_write_failed userId={} err={}", userId, e.toString());
        }
    }

    /**
     * Wipe the context entirely (e.g. on logout or explicit session reset).
     */
    public void clear(long userId) {
        redis.delete(contextKey(userId));
    }

    /**
     * Convenience getter returning only the FSM state.
     * Prefer {@link #get(long)} for all new code.
     */
    public ConversationState getState(long userId) {
        return get(userId).state();
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private static String contextKey(long userId) {
        return "chat:ctx:" + userId;
    }
}
