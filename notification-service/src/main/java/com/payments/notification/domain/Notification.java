package com.payments.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** A message "sent" to the merchant (stored + logged; a real system would email / webhook it). */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false, length = 64)
    private String merchantId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static Notification of(String merchantId, UUID paymentId, String type, String message, Instant now) {
        Notification n = new Notification();
        n.id = UUID.randomUUID();
        n.merchantId = merchantId;
        n.paymentId = paymentId;
        n.type = type;
        n.message = message;
        n.createdAt = now;
        return n;
    }
}
