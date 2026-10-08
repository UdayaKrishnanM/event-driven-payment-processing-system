package com.payments.ledger.messaging;

import com.payments.common.events.PaymentEvent;
import com.payments.common.kafka.Topics;
import com.payments.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthorizedPaymentConsumer {

    private final LedgerService ledgerService;

    @KafkaListener(topics = Topics.PAYMENT_AUTHORIZED, groupId = "ledger-service")
    public void onAuthorized(PaymentEvent event) {
        ledgerService.settle(event); // @Transactional: dedupe, post entries, outbox payment.settled
    }
}
