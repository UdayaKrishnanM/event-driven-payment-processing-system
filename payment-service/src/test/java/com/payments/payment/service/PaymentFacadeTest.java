package com.payments.payment.service;

import com.payments.payment.dto.PaymentResponse;
import com.payments.payment.exception.InvalidIdempotencyKeyException;
import com.payments.payment.idempotency.IdempotencyService;
import com.payments.payment.idempotency.IdempotentResult;
import com.payments.payment.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentFacadeTest {

    private static final String KEY = "3F0C8A52-7D1E-4E7B-9A43-1B2C3D4E5F60";

    private IdempotencyService idempotencyService;
    private PaymentService paymentService;
    private PaymentFacade facade;
    private final PaymentResponse response = PaymentResponse.from(TestData.payment());

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        idempotencyService = mock(IdempotencyService.class);
        paymentService = mock(PaymentService.class);
        facade = new PaymentFacade(idempotencyService, paymentService);
        // run the supplied action like a fresh key would
        when(idempotencyService.execute(anyString(), anyString(), eq(PaymentResponse.class), any(Supplier.class)))
                .thenAnswer(inv -> new IdempotentResult<>(((Supplier<PaymentResponse>) inv.getArgument(3)).get(), false));
    }

    @Test
    void createsThroughIdempotencyWithNormalizedKey() {
        String normalized = KEY.toLowerCase();
        when(paymentService.create(normalized, TestData.request())).thenReturn(response);

        IdempotentResult<PaymentResponse> result = facade.create(KEY, TestData.request());

        assertThat(result.body()).isEqualTo(response);
        assertThat(result.replayed()).isFalse();
    }

    @Test
    void rejectsKeysThatAreNotUuids() {
        assertThatThrownBy(() -> facade.create("not-a-uuid", TestData.request()))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
        assertThatThrownBy(() -> PaymentFacade.normalizeKey(null))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
    }

    @Test
    void databaseUniqueConstraintIsTheBackstopWhenRedisLostTheKey() {
        String normalized = KEY.toLowerCase();
        when(paymentService.create(normalized, TestData.request()))
                .thenThrow(new DataIntegrityViolationException("uq_payments_idempotency_key"));
        when(paymentService.findByIdempotencyKey(normalized)).thenReturn(Optional.of(response));

        assertThat(facade.create(KEY, TestData.request()).body()).isEqualTo(response);
    }

    @Test
    void otherIntegrityErrorsAreRethrown() {
        String normalized = KEY.toLowerCase();
        when(paymentService.create(normalized, TestData.request()))
                .thenThrow(new DataIntegrityViolationException("something else"));
        when(paymentService.findByIdempotencyKey(normalized)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.create(KEY, TestData.request()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
