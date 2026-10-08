package com.payments.payment.messaging;

import com.payments.common.events.PaymentEvent;
import com.payments.common.kafka.Topics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** The authorizer. Thin: hands the event to AuthorizationProcessor. */
@Component
@RequiredArgsConstructor
public class PaymentInitiatedConsumer {

    private final AuthorizationProcessor authorizationProcessor;

    @KafkaListener(topics = Topics.PAYMENT_INITIATED, groupId = "payment-authorizer")
    public void onInitiated(PaymentEvent event) {
        authorizationProcessor.process(event);
    }
}
