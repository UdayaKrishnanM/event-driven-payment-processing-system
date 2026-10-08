package com.payments.notification.messaging;

import com.payments.common.events.PaymentEvent;
import com.payments.common.kafka.Topics;
import com.payments.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** One notification per outcome: authorized, declined, settled. */
@Component
@RequiredArgsConstructor
public class PaymentOutcomeConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = {Topics.PAYMENT_AUTHORIZED, Topics.PAYMENT_DECLINED, Topics.PAYMENT_SETTLED},
            groupId = "notification-service")
    public void onOutcome(PaymentEvent event) {
        notificationService.record(event);
    }
}
