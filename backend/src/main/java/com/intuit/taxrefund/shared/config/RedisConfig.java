package com.intuit.taxrefund.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Minimal Redis configuration:
 * - StringRedisTemplate for simple string keys/values (rate limit, cache JSON as String).
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory cf) {
        StringRedisTemplate t = new StringRedisTemplate(cf);
        // Explicit serializers to avoid surprises
        t.setKeySerializer(StringRedisSerializer.UTF_8);
        t.setValueSerializer(StringRedisSerializer.UTF_8);
        t.setHashKeySerializer(StringRedisSerializer.UTF_8);
        t.setHashValueSerializer(StringRedisSerializer.UTF_8);
        return t;
    }
}