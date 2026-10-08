package com.payments.payment.dto;

import com.payments.payment.support.TestData;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    private Set<String> invalidFields(PaymentRequest r) {
        return validator.validate(r).stream().map(ConstraintViolation::getPropertyPath).map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void validRequestPasses() {
        assertThat(validator.validate(TestData.request())).isEmpty();
    }

    @Test
    void rejectsBadValues() {
        assertThat(invalidFields(new PaymentRequest("MER-1", "4111111111111112", 12, 2028, new BigDecimal("1"), "INR")))
                .containsExactly("cardNumber");
        assertThat(invalidFields(new PaymentRequest("MER-1", TestData.VISA_OK, 12, 2028, BigDecimal.ZERO, "INR")))
                .containsExactly("amount");
        assertThat(invalidFields(new PaymentRequest("MER-1", TestData.VISA_OK, 12, 2028, new BigDecimal("100000.01"), "INR")))
                .containsExactly("amount");
        assertThat(invalidFields(new PaymentRequest("MER-1", TestData.VISA_OK, 12, 2028, new BigDecimal("1.001"), "INR")))
                .containsExactly("amount");
        assertThat(invalidFields(new PaymentRequest("MER-1", TestData.VISA_OK, 12, 2028, new BigDecimal("1"), "GBP")))
                .containsExactly("currency");
        assertThat(invalidFields(new PaymentRequest("MER-1", TestData.VISA_OK, 1, 2020, new BigDecimal("1"), "INR")))
                .containsExactly("expiryYear");
        assertThat(invalidFields(new PaymentRequest(" ", TestData.VISA_OK, 13, 2028, new BigDecimal("1"), "INR")))
                .contains("merchantId", "expiryMonth");
    }

    @Test
    void maximumAmountIsAccepted() {
        assertThat(validator.validate(TestData.request(TestData.VISA_OK, new BigDecimal("100000.00")))).isEmpty();
    }

    @Test
    void canonicalFormIgnoresAmountScaleAndCardFormatting() {
        var a = new PaymentRequest("MER-1", "4111111111111111", 12, 2028, new BigDecimal("2499.0"), "INR");
        var b = new PaymentRequest("MER-1", "4111 1111 1111 1111", 12, 2028, new BigDecimal("2499.00"), "INR");
        var c = new PaymentRequest("MER-1", "4111111111111111", 12, 2028, new BigDecimal("2499.01"), "INR");

        assertThat(a.canonicalForm()).isEqualTo(b.canonicalForm()).isNotEqualTo(c.canonicalForm());
        assertThat(new PaymentRequest(null, null, null, null, null, null).canonicalForm()).contains("null");
    }

    @Test
    void toStringNeverContainsTheCardNumber() {
        assertThat(TestData.request().toString()).doesNotContain(TestData.VISA_OK).contains("**** 1111");
    }
}
