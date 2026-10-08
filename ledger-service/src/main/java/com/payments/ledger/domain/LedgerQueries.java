package com.payments.ledger.domain;

import com.payments.ledger.dto.CurrencyBalance;
import com.payments.ledger.dto.TrialBalance;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/** Aggregate queries in plain SQL (per currency: never add INR to USD). */
@Repository
@RequiredArgsConstructor
public class LedgerQueries {

    static final String ACCOUNT_BALANCE_SQL = """
            SELECT currency,
                   COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount END), 0) AS credits,
                   COALESCE(SUM(CASE WHEN direction = 'DEBIT'  THEN amount END), 0) AS debits,
                   COUNT(DISTINCT payment_id) AS payments
              FROM ledger_entries
             WHERE account = ?
             GROUP BY currency
             ORDER BY currency
            """;

    static final String TRIAL_BALANCE_SQL = """
            SELECT currency,
                   COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount END), 0) AS credits,
                   COALESCE(SUM(CASE WHEN direction = 'DEBIT'  THEN amount END), 0) AS debits,
                   COUNT(DISTINCT payment_id) AS payments
              FROM ledger_entries
             GROUP BY currency
             ORDER BY currency
            """;

    private final JdbcTemplate jdbcTemplate;

    public List<CurrencyBalance> balancesForAccount(String account) {
        return jdbcTemplate.query(ACCOUNT_BALANCE_SQL, (rs, i) -> CurrencyBalance.of(rs.getString("currency"),
                rs.getBigDecimal("credits"), rs.getBigDecimal("debits"), rs.getLong("payments")), account);
    }

    /** Sum of all debits vs all credits. In a correct double-entry ledger they are always equal. */
    public TrialBalance trialBalance() {
        List<CurrencyBalance> perCurrency = jdbcTemplate.query(TRIAL_BALANCE_SQL, (rs, i) -> CurrencyBalance.of(
                rs.getString("currency"), rs.getBigDecimal("credits"), rs.getBigDecimal("debits"),
                rs.getLong("payments")));
        BigDecimal debits = perCurrency.stream().map(CurrencyBalance::debits).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = perCurrency.stream().map(CurrencyBalance::credits).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean balanced = perCurrency.stream().allMatch(b -> b.debits().compareTo(b.credits()) == 0);
        return new TrialBalance(debits, credits, balanced, perCurrency);
    }
}
