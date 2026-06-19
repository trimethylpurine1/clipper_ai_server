package com.clipperai.product.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class StripeConfig {

    @Value("${stripe.secret-key}")
    private String secretKey;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${stripe.publishable-key}")
    private String publishableKey;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @PostConstruct
    void init() {
        Stripe.apiKey = secretKey;
    }
}