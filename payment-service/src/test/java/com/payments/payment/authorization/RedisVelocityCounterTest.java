package com.payments.payment.authorization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class RedisVelocityCounterTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private RedisVelocityCounter counter;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        counter = new RedisVelocityCounter(redis, Duration.ofSeconds(60));
    }

    @Test
    void firstPaymentStartsTheWindow() {
        when(ops.increment("velocity:fp")).thenReturn(1L);

        assertThat(counter.increment("fp")).isEqualTo(1L);
        verify(redis).expire("velocity:fp", Duration.ofSeconds(60));
    }

    @Test
    void laterPaymentsOnlyIncrement() {
        when(ops.increment("velocity:fp")).thenReturn(4L);

        assertThat(counter.increment("fp")).isEqualTo(4L);
        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void nullFromRedisCountsAsZero() {
        when(ops.increment("velocity:fp")).thenReturn(null);

        assertThat(counter.increment("fp")).isZero();
    }
}
