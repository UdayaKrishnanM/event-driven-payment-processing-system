package com.payments.payment.domain;

import com.payments.common.domain.PaymentStatus;
import com.payments.common.util.CardMasker;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A card payment. Only the masked card ("**** 1111") and a keyed hash (fingerprint, used by the velocity rule)
 * are stored - never the full card number. {@code @Version} gives optimistic locking: a stale update fails
 * instead of silently overwriting a newer status.
 */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "merchant_id", nullable = false, length = 64)
    private String merchantId;

    @Column(name = "masked_card", nullable = false, length = 19)
    private String maskedCard;

    @Column(name = "card_fingerprint", nullable = false, length = 64)
    private String cardFingerprint;

    @Column(name = "expiry_month", nullable = false)
    private int expiryMonth;

    @Column(name = "expiry_year", nullable = false)
    private int expiryYear;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "auth_code", length = 6)
    private String authCode;

    @Column(name = "decline_reason", length = 40)
    private String declineReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @SuppressWarnings("java:S107") // a factory for a value-rich entity
    public static Payment receive(UUID id, String idempotencyKey, String merchantId, String maskedCard,
                                  String cardFingerprint, int expiryMonth, int expiryYear, BigDecimal amount,
                                  String currency, Instant now) {
        Payment p = new Payment();
        p.id = id;
        p.idempotencyKey = idempotencyKey;
        p.merchantId = merchantId;
        p.maskedCard = maskedCard;
        p.cardFingerprint = cardFingerprint;
        p.expiryMonth = expiryMonth;
        p.expiryYear = expiryYear;
        p.amount = amount;
        p.currency = currency;
        p.status = PaymentStatus.RECEIVED;
        p.createdAt = now;
        p.updatedAt = now;
        return p;
    }

    public void authorize(String authCode, Instant now) {
        this.status = status.transitionTo(PaymentStatus.AUTHORIZED);
        this.authCode = authCode;
        this.updatedAt = now;
    }

    public void decline(String reason, Instant now) {
        this.status = status.transitionTo(PaymentStatus.DECLINED);
        this.declineReason = reason;
        this.updatedAt = now;
    }

    public void settle(Instant now) {
        this.status = status.transitionTo(PaymentStatus.SETTLED);
        this.updatedAt = now;
    }

    public String last4() {
        return CardMasker.last4(maskedCard);
    }
}
