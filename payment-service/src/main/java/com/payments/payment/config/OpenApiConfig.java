package com.payments.payment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentOpenApi() {
        return new OpenAPI().info(new Info()
                .title("payment-service API")
                .version("v1")
                .description("Create card payments (Idempotency-Key header required) and read their status."));
    }
}
