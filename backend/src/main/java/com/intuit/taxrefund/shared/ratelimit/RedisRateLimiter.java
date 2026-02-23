package com.intuit.taxrefund.shared.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class RedisRateLimiter {

    private final StringRedisTemplate redis;

    private static final String LUA = """
        local key = KEYS[1]
        local now = tonumber(ARGV[1])
        local capacity = tonumber(ARGV[2])
        local refillRate = tonumber(ARGV[3])
        local requested = tonumber(ARGV[4])

        local data = redis.call('HMGET', key, 'tokens', 'ts')
        local tokens = tonumber(data[1])
        local ts = tonumber(data[2])

        if tokens == nil then tokens = capacity end
        if ts == nil then ts = now end

        local delta = math.max(0, now - ts)
        local refill = delta * refillRate
        tokens = math.min(capacity, tokens + refill)

        local allowed = 0
        if tokens >= requested then
          allowed = 1
          tokens = tokens - requested
        end

        redis.call('HMSET', key, 'tokens', tokens, 'ts', now)
        redis.call('PEXPIRE', key, 10 * 60 * 1000)

        return {allowed, tokens}
    """;

    // Tell Spring the script returns a 2-element list
    private static final DefaultRedisScript<List> SCRIPT;
    static {
        SCRIPT = new DefaultRedisScript<>();
        SCRIPT.setScriptText(LUA);
        SCRIPT.setResultType(List.class);
    }

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Result tryConsume(String key, int capacity, int refillPerMinute, int tokens) {
        long now = Instant.now().toEpochMilli();
        double refillPerMillis = refillPerMinute / 60000.0;

        @SuppressWarnings("unchecked")
        List<Object> resp = (List<Object>) redis.execute(
            SCRIPT,
            List.of(key),
            String.valueOf(now),
            String.valueOf(capacity),
            String.valueOf(refillPerMillis),
            String.valueOf(tokens)
        );

        // Defensive: null if Redis down or script failed
        if (resp == null || resp.size() < 2) {
            return new Result(true, capacity); // or fail-closed if you prefer
        }

        int allowed = Integer.parseInt(resp.get(0).toString());
        double remaining = Double.parseDouble(resp.get(1).toString());

        return new Result(allowed == 1, remaining);
    }

    public record Result(boolean allowed, double remainingTokens) {}
}