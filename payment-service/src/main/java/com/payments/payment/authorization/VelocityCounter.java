package com.payments.payment.authorization;

/** Counts payments per card inside a time window. */
public interface VelocityCounter {

    /** Adds one payment for this card and returns the count inside the current window. */
    long increment(String cardFingerprint);
}
