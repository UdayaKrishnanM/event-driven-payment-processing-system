package com.payments.ledger.dto;

import java.math.BigDecimal;

/** balance = credits - debits */
public record CurrencyBalance(String currency, BigDecimal credits, BigDecimal debits, BigDecimal balance,
                              long payments) {

    public static CurrencyBalance of(String currency, BigDecimal credits, BigDecimal debits, long payments) {
        return new CurrencyBalance(currency.trim(), credits, debits, credits.subtract(debits), payments);
    }
}
