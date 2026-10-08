package com.payments.ledger.service;

import com.payments.ledger.domain.Accounts;
import com.payments.ledger.domain.Direction;
import com.payments.ledger.domain.LedgerEntry;
import com.payments.ledger.domain.LedgerEntryRepository;
import com.payments.ledger.domain.LedgerQueries;
import com.payments.ledger.dto.CurrencyBalance;
import com.payments.ledger.dto.TrialBalance;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LedgerQueryServiceTest {

    private final LedgerEntryRepository repository = mock(LedgerEntryRepository.class);
    private final LedgerQueries queries = mock(LedgerQueries.class);
    private final LedgerQueryService service = new LedgerQueryService(repository, queries);

    @Test
    void entriesForPaymentAreBalanced() {
        UUID id = UUID.randomUUID();
        when(repository.findByPaymentIdOrderByDirectionAsc(id)).thenReturn(List.of(
                LedgerEntry.of(id, Accounts.merchant("M"), Direction.CREDIT, new BigDecimal("5.00"), "INR", Instant.now()),
                LedgerEntry.of(id, Accounts.CUSTOMER_CLEARING, Direction.DEBIT, new BigDecimal("5.00"), "INR", Instant.now())));

        var r = service.entriesForPayment(id);

        assertThat(r.entries()).hasSize(2);
        assertThat(r.balanced()).isTrue();
    }

    @Test
    void oneSidedEntriesAreNotBalanced() {
        UUID id = UUID.randomUUID();
        assertThat(LedgerQueryService.isBalanced(List.of(
                LedgerEntry.of(id, "X", Direction.DEBIT, BigDecimal.ONE, "INR", Instant.now())))).isFalse();
        assertThat(LedgerQueryService.isBalanced(List.of())).isTrue();
    }

    @Test
    void merchantBalanceUsesMerchantAccount() {
        when(queries.balancesForAccount("MERCHANT:MER-1"))
                .thenReturn(List.of(CurrencyBalance.of("INR", new BigDecimal("10.00"), BigDecimal.ZERO, 2)));

        var r = service.merchantBalance("MER-1");

        assertThat(r.account()).isEqualTo("MERCHANT:MER-1");
        assertThat(r.balances().get(0).balance()).isEqualByComparingTo("10.00");
    }

    @Test
    void trialBalanceIsPassedThrough() {
        var tb = new TrialBalance(BigDecimal.TEN, BigDecimal.TEN, true, List.of());
        when(queries.trialBalance()).thenReturn(tb);

        assertThat(service.trialBalance()).isSameAs(tb);
    }
}
