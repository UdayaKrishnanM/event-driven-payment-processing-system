package com.payments.payment.support;

import com.payments.payment.domain.Payment;
import com.payments.payment.dto.PaymentRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class TestData {

    public static final String VISA_OK = "4111111111111111";
    public static final String CARD_0002 = "4000000000000002";
    public static final Instant NOW = Instant.parse("2026-10-20T10:15:30Z");

    private TestData() {
    }

    public static PaymentRequest request() {
        return request(VISA_OK, new BigDecimal("2499.00"));
    }

    public static PaymentRequest request(String card, BigDecimal amount) {
        return new PaymentRequest("MER-1001", card, 12, 2028, amount, "INR");
    }

    public static Payment payment() {
        return payment("**** 1111", new BigDecimal("2499.00"));
    }

    public static Payment payment(String maskedCard, BigDecimal amount) {
        return Payment.receive(UUID.randomUUID(), UUID.randomUUID().toString(), "MER-1001", maskedCard, "fp",
                12, 2028, amount, "INR", NOW);
    }
}
