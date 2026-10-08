package com.payments.ledger.dto;

import java.math.BigDecimal;
import java.util.List;

public record TrialBalance(BigDecimal totalDebits, BigDecimal totalCredits, boolean balanced,
                           List<CurrencyBalance> byCurrency) {
}
