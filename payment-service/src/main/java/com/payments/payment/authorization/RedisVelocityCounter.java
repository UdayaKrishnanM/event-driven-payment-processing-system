package com.payments.payment.authorization;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Redis INCR + EXPIRE: a fixed-window counter per card fingerprint (default 60 seconds). */
@Component
public class RedisVelocityCounter implements VelocityCounter {

    static final String KEY_PREFIX = "velocity:";

    private final StringRedisTemplate redis;
    private final Duration window;

    public RedisVelocityCounter(StringRedisTemplate redis,
                                @Value("${payments.authorization.velocity.window:PT60S}") Duration window) {
        this.redis = redis;
        this.window = window;
    }

    @Override
    public long increment(String cardFingerprint) {
        String key = KEY_PREFIX + cardFingerprint;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            // First payment in this window starts the clock.
            redis.expire(key, window);
        }
        return count == null ? 0L : count;
    }
}
