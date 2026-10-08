package com.payments.notification.service;

import com.payments.common.dedup.EventDeduplicator;
import com.payments.common.events.PaymentEvent;
import com.payments.notification.domain.Notification;
import com.payments.notification.domain.NotificationRepository;
import com.payments.notification.dto.NotificationResponse;
import com.payments.notification.dto.PagedResponse;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    static final int MAX_PAGE_SIZE = 100;

    private final EventDeduplicator deduplicator;
    private final NotificationRepository repository;
    private final Clock clock;

    /** Saves one merchant notification per outcome event (de-duplicated on eventId). */
    @Transactional
    public void record(PaymentEvent event) {
        if (event.eventType() == null || event.paymentId() == null || event.merchantId() == null) {
            throw new ValidationException("Event is missing eventType/paymentId/merchantId: " + event);
        }
        if (!deduplicator.firstDelivery(event.eventId())) {
            return;
        }
        String message = messageFor(event);
        repository.save(Notification.of(event.merchantId(), event.paymentId(), event.eventType().name(), message,
                clock.instant()));
        log.info("Notification to merchant {}: {}", event.merchantId(), message);
    }

    static String messageFor(PaymentEvent e) {
        String money = e.currency() + " " + (e.amount() == null ? "?" : e.amount().toPlainString());
        return switch (e.eventType()) {
            case PAYMENT_AUTHORIZED -> "Payment %s of %s on card %s was authorized (auth code %s)."
                    .formatted(e.paymentId(), money, e.maskedCard(), e.authCode());
            case PAYMENT_DECLINED -> "Payment %s of %s on card %s was declined: %s."
                    .formatted(e.paymentId(), money, e.maskedCard(), e.declineReason());
            case PAYMENT_SETTLED -> "Payment %s of %s has been settled to your account."
                    .formatted(e.paymentId(), money);
            case PAYMENT_INITIATED -> throw new ValidationException("No notification for " + e.eventType());
        };
    }

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> list(String merchantId, UUID paymentId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        Page<Notification> result;
        if (paymentId != null) {
            result = repository.findByPaymentIdOrderByCreatedAtDesc(paymentId, pageable);
        } else if (merchantId != null && !merchantId.isBlank()) {
            result = repository.findByMerchantIdOrderByCreatedAtDesc(merchantId, pageable);
        } else {
            result = repository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return PagedResponse.from(result, NotificationResponse::from);
    }
}
