package com.payments.payment.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Kept out of the main class on purpose: @WebMvcTest / @DataJpaTest slices skip @Configuration classes,
 * so they don't need a Redis cache manager or the outbox scheduler.
 */
@Configuration
@EnableScheduling
@EnableCaching
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
