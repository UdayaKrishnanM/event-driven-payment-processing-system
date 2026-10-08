package com.payments.payment.domain;

import com.payments.common.domain.IllegalStateTransitionException;
import com.payments.common.domain.PaymentStatus;
import com.payments.payment.support.TestData;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private final Instant later = TestData.NOW.plusSeconds(2);

    @Test
    void newPaymentIsReceived() {
        Payment p = TestData.payment();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.RECEIVED);
        assertThat(p.last4()).isEqualTo("1111");
        assertThat(p.getCreatedAt()).isEqualTo(p.getUpdatedAt());
    }

    @Test
    void happyPathReceivedAuthorizedSettled() {
        Payment p = TestData.payment();
        p.authorize("A1B2C3", later);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(p.getAuthCode()).isEqualTo("A1B2C3");
        p.settle(later.plusSeconds(1));
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.SETTLED);
        assertThat(p.getUpdatedAt()).isEqualTo(later.plusSeconds(1));
    }

    @Test
    void declineRecordsReason() {
        Payment p = TestData.payment();
        p.decline("LIMIT_EXCEEDED", later);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(p.getDeclineReason()).isEqualTo("LIMIT_EXCEEDED");
    }

    @Test
    void settledPaymentCanNeverMoveBackwards() {
        Payment p = TestData.payment();
        p.authorize("A1B2C3", later);
        p.settle(later);
        assertThatThrownBy(() -> p.authorize("ZZZZZZ", later)).isInstanceOf(IllegalStateTransitionException.class);
        assertThatThrownBy(() -> p.decline("X", later)).isInstanceOf(IllegalStateTransitionException.class);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.SETTLED);
    }

    @Test
    void cannotSettleWithoutAuthorization() {
        Payment p = TestData.payment();
        assertThatThrownBy(() -> p.settle(later)).isInstanceOf(IllegalStateTransitionException.class);
    }
}
