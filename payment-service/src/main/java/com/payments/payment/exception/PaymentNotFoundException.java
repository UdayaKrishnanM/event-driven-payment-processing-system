package com.payments.payment.exception;

import com.payments.common.web.ResourceNotFoundException;

import java.util.UUID;

public class PaymentNotFoundException extends ResourceNotFoundException {

    public PaymentNotFoundException(UUID paymentId) {
        super("PAYMENT_NOT_FOUND", "Payment " + paymentId + " not found");
    }
}
