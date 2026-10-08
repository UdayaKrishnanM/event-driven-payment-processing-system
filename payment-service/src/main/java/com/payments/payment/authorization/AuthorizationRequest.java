package com.payments.payment.authorization;

import java.math.BigDecimal;
import java.util.UUID;

/** What the simulated issuer sees. No full card number: last 4 digits + keyed fingerprint only. */
public record AuthorizationRequest(UUID paymentId, BigDecimal amount, String cardLast4, int expiryMonth,
                                   int expiryYear, String cardFingerprint) {
}
