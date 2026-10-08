package com.payments.ledger.domain;

import com.payments.ledger.dto.CurrencyBalance;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LedgerQueriesTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final LedgerQueries queries = new LedgerQueries(jdbc);

    private static ResultSet row(String currency, String credits, String debits, long payments) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("currency")).thenReturn(currency);
        when(rs.getBigDecimal("credits")).thenReturn(new BigDecimal(credits));
        when(rs.getBigDecimal("debits")).thenReturn(new BigDecimal(debits));
        when(rs.getLong("payments")).thenReturn(payments);
        return rs;
    }

    @Test
    @SuppressWarnings("unchecked")
    void accountBalanceIsCreditsMinusDebits() throws Exception {
        ResultSet inr = row("INR", "300.00", "0.00", 3);
        doAnswer(inv -> {
            RowMapper<CurrencyBalance> m = inv.getArgument(1);
            return List.of(m.mapRow(inr, 0));
        }).when(jdbc).query(eq(LedgerQueries.ACCOUNT_BALANCE_SQL), any(RowMapper.class), eq("MERCHANT:M"));

        var balances = queries.balancesForAccount("MERCHANT:M");

        assertThat(balances).singleElement().satisfies(b -> {
            assertThat(b.currency()).isEqualTo("INR");
            assertThat(b.balance()).isEqualByComparingTo("300.00");
            assertThat(b.payments()).isEqualTo(3);
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void trialBalanceSumsAllCurrencies() throws Exception {
        ResultSet inr = row("INR", "100.00", "100.00", 1);
        ResultSet usd = row("USD", "5.00", "5.00", 1);
        doAnswer(inv -> {
            RowMapper<CurrencyBalance> m = inv.getArgument(1);
            return List.of(m.mapRow(inr, 0), m.mapRow(usd, 1));
        }).when(jdbc).query(eq(LedgerQueries.TRIAL_BALANCE_SQL), any(RowMapper.class));

        var tb = queries.trialBalance();

        assertThat(tb.totalDebits()).isEqualByComparingTo("105.00");
        assertThat(tb.totalCredits()).isEqualByComparingTo("105.00");
        assertThat(tb.balanced()).isTrue();
        assertThat(tb.byCurrency()).hasSize(2);
    }
}
