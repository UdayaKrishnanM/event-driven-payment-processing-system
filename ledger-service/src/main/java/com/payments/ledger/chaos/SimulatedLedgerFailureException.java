package com.payments.ledger.chaos;

/** A deliberately transient-looking failure (retryable), used only by the chaos demo. */
public class SimulatedLedgerFailureException extends RuntimeException {

    public SimulatedLedgerFailureException(String merchantId) {
        super("Simulated ledger database failure for merchant " + merchantId + " (chaos mode)");
    }
}
