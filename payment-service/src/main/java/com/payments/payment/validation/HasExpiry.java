package com.payments.payment.validation;

/** Implemented by request types that carry a card expiry. */
public interface HasExpiry {

    Integer expiryMonth();

    Integer expiryYear();
}
