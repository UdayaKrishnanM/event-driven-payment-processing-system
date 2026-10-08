package com.payments.ledger.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;

/** Real Kafka and PostgreSQL, started once per test JVM. */
public abstract class ContainersSupport {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.7.0");

    static {
        Startables.deepStart(POSTGRES, KAFKA).join();
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("payments.outbox.poll-interval-ms", () -> "100");
    }

    protected static String bootstrapServers() {
        return KAFKA.getBootstrapServers();
    }
}
