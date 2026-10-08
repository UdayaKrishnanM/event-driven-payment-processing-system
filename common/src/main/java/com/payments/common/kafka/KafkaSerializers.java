package com.payments.common.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Producer value serializer that picks the right serializer by the value's type:
 * <ul>
 *   <li>{@code byte[]}  - raw bytes (used by the DLT recoverer for records that could not be deserialized)</li>
 *   <li>{@code String}  - already-serialized JSON (used by the outbox publisher and DLT replay)</li>
 *   <li>anything else  - JSON without type headers (consumers use a fixed default type)</li>
 * </ul>
 */
public final class KafkaSerializers {

    private KafkaSerializers() {
    }

    public static Serializer<Object> valueSerializer(ObjectMapper objectMapper) {
        Map<Class<?>, Serializer<?>> delegates = new LinkedHashMap<>();
        delegates.put(byte[].class, new ByteArraySerializer());
        delegates.put(String.class, new StringSerializer());
        delegates.put(Object.class, new JsonSerializer<>(objectMapper).noTypeInfo());
        return new DelegatingByTypeSerializer(delegates, true);
    }
}
