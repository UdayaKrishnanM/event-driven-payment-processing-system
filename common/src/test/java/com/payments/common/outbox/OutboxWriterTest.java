package com.payments.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.common.events.EventType;
import com.payments.common.events.PaymentEvent;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OutboxWriterTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final Instant now = Instant.parse("2026-10-20T10:15:30Z");
    private final PaymentEvent event = PaymentEvent.create(EventType.PAYMENT_INITIATED, UUID.randomUUID(), "MER-1",
            new BigDecimal("1.00"), "INR", "**** 1111", null, null, now);

    @Test
    void writesTopicKeyAndJsonPayload() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(event)).thenReturn("{json}");

        new OutboxWriter(jdbc, mapper).write(event);

        verify(jdbc).update(eq(OutboxWriter.INSERT_SQL), eq(event.eventId()), eq("payment.initiated"),
                eq(event.paymentId().toString()), eq("{json}"), eq(Timestamp.from(now)));
    }

    @Test
    void serializationFailureIsReportedAndNothingIsWritten() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("bad") { });

        assertThatThrownBy(() -> new OutboxWriter(jdbc, mapper).write(event))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(jdbc);
    }
}
