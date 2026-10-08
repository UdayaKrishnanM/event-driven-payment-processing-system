package com.payments.payment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.payment.dto.PaymentResponse;
import com.payments.payment.service.PaymentStatusCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/** Payment status cache in Redis: typed JSON values, 5-minute TTL, evicted on every status change. */
@Configuration
public class CacheConfig {

    @Bean
    public RedisCacheManagerBuilderCustomizer paymentStatusCacheCustomizer(
            ObjectMapper objectMapper, @Value("${payments.cache.status-ttl:PT5M}") Duration ttl) {
        var serializer = new Jackson2JsonRedisSerializer<>(objectMapper.copy(), PaymentResponse.class);
        return builder -> builder.withCacheConfiguration(PaymentStatusCache.CACHE_NAME,
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(ttl)
                        .disableCachingNullValues()
                        .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer)));
    }
}
