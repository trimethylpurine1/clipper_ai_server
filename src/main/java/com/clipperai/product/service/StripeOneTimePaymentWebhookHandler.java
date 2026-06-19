package com.clipperai.product.service;

import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class StripeOneTimePaymentWebhookHandler {

    public void handlePaymentIntentSucceeded(Event event, PaymentIntent paymentIntent) {
        Map<String, String> metadata = paymentIntent.getMetadata();

        if (metadata == null) {
            return;
        }

        String paymentType = metadata.get("payment_type");

        if (!"extra_credit".equals(paymentType)) {
            return;
        }

        /*
         * Later:
         *
         * UUID creditGrantId = UUID.fromString(metadata.get("credit_grant_id"));
         * creditGrantService.activateFromSuccessfulPayment(
         *         creditGrantId,
         *         paymentIntent.getId(),
         *         paymentIntent.getAmount(),
         *         paymentIntent.getCurrency()
         * );
         *
         * auditService.recordWebhookAction(
         *         AuditAction.EXTRA_CREDIT_PURCHASE_COMPLETED,
         *         "CREDIT_GRANT",
         *         creditGrantId,
         *         Map.of(...)
         * );
         */
    }

    public void handlePaymentIntentFailed(Event event, PaymentIntent paymentIntent) {
        Map<String, String> metadata = paymentIntent.getMetadata();

        if (metadata == null) {
            return;
        }

        String paymentType = metadata.get("payment_type");

        if (!"extra_credit".equals(paymentType)) {
            return;
        }

        /*
         * Later:
         *
         * UUID creditGrantId = UUID.fromString(metadata.get("credit_grant_id"));
         * creditGrantService.markPaymentFailed(
         *         creditGrantId,
         *         paymentIntent.getId()
         * );
         */
    }
}