package com.clipperai.product.controller;


import com.clipperai.product.service.billing.StripeWebhookService;
import com.stripe.exception.SignatureVerificationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@RestController
@RequestMapping("/api/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

	private final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);
	
    private final StripeWebhookService stripeWebhookService;

    @PostMapping("/webhook")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signatureHeader
    ) {
        try {
            stripeWebhookService.handleWebhook(payload, signatureHeader);
            return ResponseEntity.ok("ok");

        } catch (SignatureVerificationException invalidSignature) {
        	log.warn("Invalid Stripe webhook signature.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid Stripe webhook signature.");

        } catch (Exception processingFailed) {
            /*
             * Return 500 so Stripe retries the webhook.
             * Your stripe_webhook_events table records the failed attempt.
             */
        	log.error("Stripe webhook processing failed", processingFailed);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Webhook processing failed." + processingFailed.getMessage());
        }
    }
}