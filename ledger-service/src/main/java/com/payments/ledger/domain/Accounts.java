package com.payments.ledger.domain;

/** Ledger account names. */
public final class Accounts {

    /** Money owed by the card networks / customers for captured card payments. */
    public static final String CUSTOMER_CLEARING = "CUSTOMER_CLEARING";
    private static final String MERCHANT_PREFIX = "MERCHANT:";

    private Accounts() {
    }

    public static String merchant(String merchantId) {
        return MERCHANT_PREFIX + merchantId;
    }
}
