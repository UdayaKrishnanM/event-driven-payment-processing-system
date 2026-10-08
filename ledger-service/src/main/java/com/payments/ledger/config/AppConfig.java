package com.payments.ledger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public OpenAPI ledgerOpenApi() {
        return new OpenAPI().info(new Info().title("ledger-service API").version("v1")
                .description("Double-entry ledger: entries per payment, merchant balances, trial balance."));
    }
}
