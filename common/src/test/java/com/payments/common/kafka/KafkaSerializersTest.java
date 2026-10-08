package com.payments.common.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.payments.common.events.EventType;
import com.payments.common.events.PaymentEvent;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaSerializersTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Serializer<Object> serializer = KafkaSerializers.valueSerializer(mapper);

    @Test
    void rawBytesPassThrough() {
        byte[] raw = {1, 2, 3};
        assertThat(serializer.serialize("t", raw)).isEqualTo(raw);
    }

    @Test
    void stringsAreSentAsIs() {
        assertThat(new String(serializer.serialize("t", "{\"a\":1}"), StandardCharsets.UTF_8)).isEqualTo("{\"a\":1}");
    }

    @Test
    void objectsBecomeJson() throws Exception {
        PaymentEvent event = PaymentEvent.create(EventType.PAYMENT_SETTLED, UUID.randomUUID(), "MER-1",
                new BigDecimal("5.00"), "EUR", "**** 1111", "ABC123", null, Instant.parse("2026-01-01T00:00:00Z"));

        byte[] json = serializer.serialize("t", event);

        PaymentEvent back = mapper.readValue(json, PaymentEvent.class);
        assertThat(back).isEqualTo(event);
    }
}
