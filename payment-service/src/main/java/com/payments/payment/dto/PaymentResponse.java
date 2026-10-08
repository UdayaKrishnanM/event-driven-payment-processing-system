package com.payments.payment.dto;

import com.payments.common.domain.PaymentStatus;
import com.payments.payment.domain.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Payment as returned by the API (and stored in the Redis status cache). */
public record PaymentResponse(
        UUID paymentId,
        String merchantId,
        String maskedCard,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String authCode,
        String declineReason,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getMerchantId(), p.getMaskedCard(), p.getAmount(), p.getCurrency(),
                p.getStatus(), p.getAuthCode(), p.getDeclineReason(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
