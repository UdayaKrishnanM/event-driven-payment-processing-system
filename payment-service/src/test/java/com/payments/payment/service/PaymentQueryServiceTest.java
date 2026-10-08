package com.payments.payment.service;

import com.payments.payment.domain.Payment;
import com.payments.payment.domain.PaymentRepository;
import com.payments.payment.exception.PaymentNotFoundException;
import com.payments.payment.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentQueryServiceTest {

    private final PaymentRepository repository = mock(PaymentRepository.class);
    private final PaymentQueryService service = new PaymentQueryService(repository);

    @Test
    void returnsPayment() {
        Payment p = TestData.payment();
        when(repository.findById(p.getId())).thenReturn(Optional.of(p));

        assertThat(service.get(p.getId()).paymentId()).isEqualTo(p.getId());
    }

    @Test
    void unknownPaymentIs404() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void listClampsPageSize() {
        Payment p = TestData.payment();
        when(repository.findByMerchantIdOrderByCreatedAtDesc("MER-1001", PageRequest.of(0, 100)))
                .thenReturn(new PageImpl<>(List.of(p), PageRequest.of(0, 100), 1));

        var page = service.listByMerchant("MER-1001", -3, 5000);

        verify(repository).findByMerchantIdOrderByCreatedAtDesc("MER-1001", PageRequest.of(0, 100));
        assertThat(page.content()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1);
    }
}
