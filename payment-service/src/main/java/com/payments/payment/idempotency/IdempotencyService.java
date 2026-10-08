package com.payments.payment.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * API-layer idempotency in Redis.
 * <pre>
 *  SET idem:{key} IN_PROGRESS:{hash} NX EX 86400
 *    set      -> run the action, then SET idem:{key} DONE:{hash}:{responseJson} EX 86400
 *                (if the action throws, DEL the key so the client can retry)
 *    exists   -> different hash : 422 (key reused for another payment)
 *                IN_PROGRESS    : 409 (first request still running)
 *                DONE           : return the stored response, Idempotent-Replayed: true
 * </pre>
 * Redis is not the source of truth: the UNIQUE(idempotency_key) constraint in PostgreSQL is the backstop.
 */
@Slf4j
@Service
public class IdempotencyService {

    static final String KEY_PREFIX = "idem:";
    static final String IN_PROGRESS = "IN_PROGRESS";
    static final String DONE = "DONE";
    private static final int MAX_READ_ATTEMPTS = 3;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public IdempotencyService(StringRedisTemplate redis, ObjectMapper objectMapper,
                              @Value("${payments.idempotency.ttl:PT24H}") Duration ttl) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    public <T> IdempotentResult<T> execute(String key, String requestHash, Class<T> responseType, Supplier<T> action) {
        String redisKey = KEY_PREFIX + key;
        for (int attempt = 1; attempt <= MAX_READ_ATTEMPTS; attempt++) {
            Boolean acquired = redis.opsForValue().setIfAbsent(redisKey, IN_PROGRESS + ":" + requestHash, ttl);
            if (Boolean.TRUE.equals(acquired)) {
                return new IdempotentResult<>(runAndStore(redisKey, requestHash, action), false);
            }
            String existing = redis.opsForValue().get(redisKey);
            if (existing != null) {
                return replayOrReject(key, existing, requestHash, responseType);
            }
            // The key expired between SETNX and GET: try to acquire it again.
        }
        throw new IdempotencyInProgressException(key);
    }

    private <T> T runAndStore(String redisKey, String requestHash, Supplier<T> action) {
        T result;
        try {
            result = action.get();
        } catch (RuntimeException e) {
            redis.delete(redisKey);
            throw e;
        }
        try {
            redis.opsForValue().set(redisKey, DONE + ":" + requestHash + ":" + objectMapper.writeValueAsString(result), ttl);
        } catch (JsonProcessingException | RuntimeException e) {
            // The payment exists; a later retry is still protected by the DB unique constraint.
            log.warn("Could not store idempotent response for {}: {}", redisKey, e.getMessage());
        }
        return result;
    }

    private <T> IdempotentResult<T> replayOrReject(String key, String existing, String requestHash, Class<T> type) {
        String[] parts = existing.split(":", 3);
        String state = parts[0];
        String storedHash = parts.length > 1 ? parts[1] : "";
        if (!storedHash.equals(requestHash)) {
            throw new IdempotencyKeyReusedException(key);
        }
        if (IN_PROGRESS.equals(state) || parts.length < 3) {
            throw new IdempotencyInProgressException(key);
        }
        try {
            log.info("Replaying stored response for Idempotency-Key {}", key);
            return new IdempotentResult<>(objectMapper.readValue(parts[2], type), true);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored idempotent response is unreadable for key " + key, e);
        }
    }
}
