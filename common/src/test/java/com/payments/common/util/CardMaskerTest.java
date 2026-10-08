package com.payments.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CardMaskerTest {

    @Test
    void masksToLastFourDigits() {
        assertThat(CardMasker.mask("4111111111111111")).isEqualTo("**** 1111");
        assertThat(CardMasker.mask("4111 1111 1111 1234")).isEqualTo("**** 1234");
    }

    @Test
    void neverReturnsTheFullNumber() {
        String pan = "5555555555554444";
        assertThat(CardMasker.mask(pan)).doesNotContain(pan).hasSize(9);
    }

    @Test
    void handlesNullAndShortInput() {
        assertThat(CardMasker.mask(null)).isNull();
        assertThat(CardMasker.mask("12")).isEqualTo("****");
        assertThat(CardMasker.last4(null)).isNull();
        assertThat(CardMasker.last4("12")).isEqualTo("12");
        assertThat(CardMasker.last4("**** 0002")).isEqualTo("0002");
    }

    @Test
    void masksCardNumbersInsideFreeText() {
        String line = "charging card 4111111111111111 for order 42, backup 5555555555554444";
        assertThat(CardMasker.maskPans(line))
                .isEqualTo("charging card **** 1111 for order 42, backup **** 4444")
                .doesNotContain("4111111111111111");
    }

    @Test
    void leavesTextWithoutCardNumbersUntouched() {
        assertThat(CardMasker.maskPans("payment b7e2 amount 2499.00")).isEqualTo("payment b7e2 amount 2499.00");
        assertThat(CardMasker.maskPans("")).isEmpty();
        assertThat(CardMasker.maskPans(null)).isNull();
    }
}
