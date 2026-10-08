package com.payments.common.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentStatusTest {

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @CsvSource({
            "RECEIVED,   RECEIVED,   false",
            "RECEIVED,   AUTHORIZED, true",
            "RECEIVED,   DECLINED,   true",
            "RECEIVED,   SETTLED,    false",
            "AUTHORIZED, RECEIVED,   false",
            "AUTHORIZED, AUTHORIZED, false",
            "AUTHORIZED, SETTLED,    true",
            "AUTHORIZED, DECLINED,   false",
            "SETTLED,    RECEIVED,   false",
            "SETTLED,    AUTHORIZED, false",
            "SETTLED,    DECLINED,   false",
            "SETTLED,    SETTLED,    false",
            "DECLINED,   RECEIVED,   false",
            "DECLINED,   AUTHORIZED, false",
            "DECLINED,   SETTLED,    false",
            "DECLINED,   DECLINED,   false"
    })
    void canMoveTo_coversEveryLegalAndIllegalMove(PaymentStatus from, PaymentStatus to, boolean allowed) {
        assertThat(from.canMoveTo(to)).isEqualTo(allowed);
        if (allowed) {
            assertThat(from.transitionTo(to)).isEqualTo(to);
        } else {
            assertThatThrownBy(() -> from.transitionTo(to))
                    .isInstanceOf(IllegalStateTransitionException.class)
                    .hasMessageContaining(from + " -> " + to)
                    .satisfies(ex -> {
                        var ist = (IllegalStateTransitionException) ex;
                        assertThat(ist.getFrom()).isEqualTo(from);
                        assertThat(ist.getTo()).isEqualTo(to);
                    });
        }
    }

    @Test
    void nullIsNeverAllowed() {
        assertThat(PaymentStatus.RECEIVED.canMoveTo(null)).isFalse();
    }

    @Test
    void onlySettledAndDeclinedAreFinal() {
        assertThat(PaymentStatus.SETTLED.isFinal()).isTrue();
        assertThat(PaymentStatus.DECLINED.isFinal()).isTrue();
        assertThat(PaymentStatus.RECEIVED.isFinal()).isFalse();
        assertThat(PaymentStatus.AUTHORIZED.isFinal()).isFalse();
    }
}
