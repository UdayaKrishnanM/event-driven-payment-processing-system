package com.payments.payment.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;

/**
 * Real Kafka, PostgreSQL and Redis in Docker, started once per test JVM and shared by every *IT class.
 * {@code @ServiceConnection} wires Postgres and Redis without hard-coded URLs.
 */
public abstract class ContainersSupport {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.7.0");

    static {
        Startables.deepStart(POSTGRES, REDIS, KAFKA).join();
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("payments.outbox.poll-interval-ms", () -> "100");
    }
}
