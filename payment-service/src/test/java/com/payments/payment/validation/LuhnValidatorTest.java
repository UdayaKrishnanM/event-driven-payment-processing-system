package com.payments.payment.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LuhnValidatorTest {

    private final LuhnValidator validator = new LuhnValidator();

    @ParameterizedTest
    @ValueSource(strings = {"4111111111111111", "4000000000000002", "5555555555554444", "378282246310005"})
    void acceptsValidCards(String card) {
        assertThat(validator.isValid(card, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"4111111111111112", "1234567890123", "4111-1111-1111-1111", "abcd", "41111111111",
            "41111111111111111111"})
    void rejectsInvalidCards(String card) {
        assertThat(validator.isValid(card, null)).isFalse();
    }

    @Test
    void blankIsLeftToNotBlank() {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("  ", null)).isTrue();
        assertThat(LuhnValidator.isValidLuhn(null)).isFalse();
    }
}
