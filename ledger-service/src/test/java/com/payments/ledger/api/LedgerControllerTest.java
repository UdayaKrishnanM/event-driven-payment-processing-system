package com.payments.ledger.api;

import com.payments.ledger.chaos.ChaosSettings;
import com.payments.ledger.domain.Direction;
import com.payments.ledger.dto.CurrencyBalance;
import com.payments.ledger.dto.LedgerEntryResponse;
import com.payments.ledger.dto.MerchantBalanceResponse;
import com.payments.ledger.dto.PaymentEntriesResponse;
import com.payments.ledger.dto.TrialBalance;
import com.payments.ledger.service.LedgerQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({LedgerController.class, ChaosController.class})
@Import(ChaosSettings.class)
class LedgerControllerTest {

    @Autowired
    MockMvc mvc;
    @MockBean
    LedgerQueryService queryService;

    @Test
    void entries() throws Exception {
        UUID id = UUID.randomUUID();
        when(queryService.entriesForPayment(id)).thenReturn(new PaymentEntriesResponse(id, List.of(
                new LedgerEntryResponse(UUID.randomUUID(), "MERCHANT:M", Direction.CREDIT, BigDecimal.TEN, "INR", Instant.now()),
                new LedgerEntryResponse(UUID.randomUUID(), "CUSTOMER_CLEARING", Direction.DEBIT, BigDecimal.TEN, "INR", Instant.now())),
                true));

        mvc.perform(get("/api/v1/ledger/payments/{id}/entries", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.balanced").value(true));
    }

    @Test
    void balance() throws Exception {
        when(queryService.merchantBalance("MER-1")).thenReturn(new MerchantBalanceResponse("MER-1", "MERCHANT:MER-1",
                List.of(CurrencyBalance.of("INR", new BigDecimal("50.00"), BigDecimal.ZERO, 1))));

        mvc.perform(get("/api/v1/ledger/merchants/MER-1/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[0].balance").value(50.00));
    }

    @Test
    void trialBalance() throws Exception {
        when(queryService.trialBalance()).thenReturn(new TrialBalance(BigDecimal.ONE, BigDecimal.ONE, true, List.of()));

        mvc.perform(get("/api/v1/ledger/trial-balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanced").value(true));
    }

    @Test
    void badPaymentIdIs400() throws Exception {
        mvc.perform(get("/api/v1/ledger/payments/xyz/entries"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void chaosCanBeTurnedOnAndOff() throws Exception {
        mvc.perform(put("/api/v1/admin/chaos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"failMerchantId\":\"MER-DLT-DEMO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.failMerchantId").value("MER-DLT-DEMO"));

        mvc.perform(put("/api/v1/admin/chaos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"failMerchantId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(get("/api/v1/admin/chaos")).andExpect(status().isOk());
    }
}
