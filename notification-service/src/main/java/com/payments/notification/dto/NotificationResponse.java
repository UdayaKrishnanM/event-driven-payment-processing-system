package com.payments.notification.dto;

import com.payments.notification.domain.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, String merchantId, UUID paymentId, String type, String message,
                                   Instant createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getMerchantId(), n.getPaymentId(), n.getType(), n.getMessage(),
                n.getCreatedAt());
    }
}
