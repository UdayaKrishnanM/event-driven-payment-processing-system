package com.payments.payment.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.payments.payment.dto.PaymentResponse;
import com.payments.payment.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class IdempotencyServiceTest {

    private static final String KEY = "3f0c8a52-7d1e-4e7b-9a43-1b2c3d4e5f60";
    private static final String REDIS_KEY = "idem:" + KEY;
    private static final Duration TTL = Duration.ofHours(24);

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private IdempotencyService service;
    private final PaymentResponse response = PaymentResponse.from(TestData.payment());

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        service = new IdempotencyService(redis, mapper, TTL);
    }

    @Test
    void newKeyRunsTheActionAndStoresTheResponse() {
        when(ops.setIfAbsent(REDIS_KEY, "IN_PROGRESS:h1", TTL)).thenReturn(true);
        AtomicInteger calls = new AtomicInteger();

        var result = service.execute(KEY, "h1", PaymentResponse.class, () -> {
            calls.incrementAndGet();
            return response;
        });

        assertThat(result.replayed()).isFalse();
        assertThat(result.body()).isEqualTo(response);
        assertThat(calls.get()).isEqualTo(1);
        verify(ops).set(eq(REDIS_KEY), startsWith("DONE:h1:{"), eq(TTL));
    }

    @Test
    void sameKeySameBodyReplaysStoredResponseWithoutRunningTheAction() throws Exception {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(ops.get(REDIS_KEY)).thenReturn("DONE:h1:" + mapper.writeValueAsString(response));

        var result = service.execute(KEY, "h1", PaymentResponse.class, () -> {
            throw new AssertionError("must not run");
        });

        assertThat(result.replayed()).isTrue();
        assertThat(result.body()).isEqualTo(response);
    }

    @Test
    void sameKeyWhileFirstRequestIsRunningIs409() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(ops.get(REDIS_KEY)).thenReturn("IN_PROGRESS:h1");

        assertThatThrownBy(() -> service.execute(KEY, "h1", PaymentResponse.class, () -> response))
                .isInstanceOf(IdempotencyInProgressException.class);
    }

    @Test
    void sameKeyDifferentBodyIs422() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(ops.get(REDIS_KEY)).thenReturn("DONE:other-hash:{}");

        assertThatThrownBy(() -> service.execute(KEY, "h1", PaymentResponse.class, () -> response))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void failedActionDeletesTheKeySoTheClientCanRetry() {
        when(ops.setIfAbsent(REDIS_KEY, "IN_PROGRESS:h1", TTL)).thenReturn(true);

        assertThatThrownBy(() -> service.execute(KEY, "h1", PaymentResponse.class, () -> {
            throw new IllegalStateException("db down");
        })).isInstanceOf(IllegalStateException.class);

        verify(redis).delete(REDIS_KEY);
        verify(ops, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void keyThatKeepsExpiringBetweenSetAndGetEndsAs409() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(ops.get(REDIS_KEY)).thenReturn(null);

        assertThatThrownBy(() -> service.execute(KEY, "h1", PaymentResponse.class, () -> response))
                .isInstanceOf(IdempotencyInProgressException.class);
    }

    @Test
    void failureToStoreTheResponseStillReturnsTheResult() {
        when(ops.setIfAbsent(REDIS_KEY, "IN_PROGRESS:h1", TTL)).thenReturn(true);
        doThrow(new IllegalStateException("redis gone")).when(ops).set(anyString(), anyString(), any(Duration.class));

        var result = service.execute(KEY, "h1", PaymentResponse.class, () -> response);

        assertThat(result.body()).isEqualTo(response);
    }

    @Test
    void unreadableStoredResponseIsAnError() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(ops.get(REDIS_KEY)).thenReturn("DONE:h1:not-json");

        assertThatThrownBy(() -> service.execute(KEY, "h1", PaymentResponse.class, () -> response))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sha256IsStableHex() {
        assertThat(RequestHasher.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
