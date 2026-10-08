package com.payments.payment.messaging;

import com.payments.common.events.PaymentEvent;
import com.payments.common.kafka.Topics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Moves a payment to SETTLED when the ledger reports it posted. */
@Component
@RequiredArgsConstructor
public class PaymentSettledConsumer {

    private final SettlementProcessor settlementProcessor;

    @KafkaListener(topics = Topics.PAYMENT_SETTLED, groupId = "payment-settlement")
    public void onSettled(PaymentEvent event) {
        settlementProcessor.process(event);
    }
}
