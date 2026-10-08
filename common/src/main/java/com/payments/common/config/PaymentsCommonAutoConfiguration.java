package com.payments.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.common.dedup.EventDeduplicator;
import com.payments.common.kafka.KafkaErrorHandlers;
import com.payments.common.kafka.KafkaSerializers;
import com.payments.common.kafka.Topics;
import com.payments.common.outbox.OutboxPublisher;
import com.payments.common.outbox.OutboxWriter;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Shared wiring for all three services (registered in
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
 */
@AutoConfiguration
public class PaymentsCommonAutoConfiguration {

    /** Creates all 8 topics (4 main + 4 DLT), each with 3 partitions. Existing topics are left alone. */
    @Bean
    public KafkaAdmin.NewTopics paymentTopics(@Value("${payments.kafka.replicas:1}") int replicas) {
        NewTopic[] topics = Topics.allTopics().stream()
                .map(name -> TopicBuilder.name(name).partitions(Topics.PARTITIONS).replicas(replicas).build())
                .toArray(NewTopic[]::new);
        return new KafkaAdmin.NewTopics(topics);
    }

    /** String / byte[] / object-as-JSON value serializer for every producer. */
    @Bean
    @SuppressWarnings({"unchecked", "rawtypes"})
    public DefaultKafkaProducerFactoryCustomizer paymentsValueSerializerCustomizer(ObjectMapper objectMapper) {
        return factory -> ((DefaultKafkaProducerFactory) factory)
                .setValueSerializer(KafkaSerializers.valueSerializer(objectMapper));
    }

    /** Exponential backoff (1s, 2s, 4s) then dead-letter topic. Picked up by Boot's default listener container factory. */
    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate,
                                                @Value("${payments.kafka.retry.max-retries:3}") int maxRetries,
                                                @Value("${payments.kafka.retry.initial-interval-ms:1000}") long initial,
                                                @Value("${payments.kafka.retry.multiplier:2.0}") double multiplier,
                                                @Value("${payments.kafka.retry.max-interval-ms:10000}") long max) {
        return KafkaErrorHandlers.exponentialBackoffWithDlt(kafkaTemplate, maxRetries, initial, multiplier, max);
    }

    @Bean
    @ConditionalOnMissingBean
    public EventDeduplicator eventDeduplicator(JdbcTemplate jdbcTemplate) {
        return new EventDeduplicator(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "payments.outbox.enabled", havingValue = "true")
    public OutboxWriter outboxWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new OutboxWriter(jdbcTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "payments.outbox.enabled", havingValue = "true")
    public OutboxPublisher outboxPublisher(JdbcTemplate jdbcTemplate, KafkaTemplate<String, Object> kafkaTemplate,
                                           PlatformTransactionManager transactionManager,
                                           @Value("${payments.outbox.batch-size:500}") int batchSize,
                                           @Value("${payments.outbox.send-timeout-ms:10000}") long sendTimeoutMs) {
        return new OutboxPublisher(jdbcTemplate, kafkaTemplate, new TransactionTemplate(transactionManager),
                batchSize, sendTimeoutMs);
    }
}
