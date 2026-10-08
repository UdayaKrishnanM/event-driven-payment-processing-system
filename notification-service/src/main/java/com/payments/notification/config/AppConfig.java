package com.payments.notification.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class AppConfig {

    public static final String DLT_CONTAINER_FACTORY = "dltListenerContainerFactory";

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public OpenAPI notificationOpenApi() {
        return new OpenAPI().info(new Info().title("notification-service API").version("v1")
                .description("Merchant notifications and dead-letter (failed) events with replay."));
    }

    /**
     * Separate consumer for *.DLT topics: values are read as plain Strings (a dead letter may not even be valid JSON),
     * and failures here are only retried twice and logged - a DLT monitor must never feed another DLT.
     */
    @Bean(name = DLT_CONTAINER_FACTORY)
    public ConcurrentKafkaListenerContainerFactory<String, String> dltListenerContainerFactory(
            KafkaProperties kafkaProperties) {
        Map<String, Object> config = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, 30_000); // discover new *.DLT topics quickly

        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(config));
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1_000L, 2L)));
        return factory;
    }
}
