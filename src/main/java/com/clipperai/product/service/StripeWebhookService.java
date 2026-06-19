package com.clipperai.product.service;

import com.clipperai.product.entity.StripeWebhookEvent;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.*;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    private final StripeWebhookEventService stripeWebhookEventService;
    private final StripeSubscriptionWebhookHandler stripeSubscriptionWebhookHandler;
    private final StripeOneTimePaymentWebhookHandler stripeOneTimePaymentWebhookHandler;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    public void handleWebhook(String payload, String signatureHeader)
            throws SignatureVerificationException {

        Event event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);

        StripeObject stripeObject = event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new IllegalStateException(
                        "Unable to deserialize Stripe webhook object for event: " + event.getId()
                ));

        String stripeObjectId = extractStripeObjectId(stripeObject);

        StripeWebhookEvent webhookEvent =
                stripeWebhookEventService.recordReceivedOrGetExisting(
                        event.getId(),
                        event.getType(),
                        stripeObjectId
                );

        if ("processed".equalsIgnoreCase(webhookEvent.getStatus())) {
            return;
        }

        try {
            dispatch(event, stripeObject);
            stripeWebhookEventService.markProcessed(event.getId());

        } catch (Exception processingFailed) {
            stripeWebhookEventService.markFailed(event.getId(), processingFailed);
            throw processingFailed;
        }
    }

    private void dispatch(Event event, StripeObject stripeObject) {
        switch (event.getType()) {
            case "customer.subscription.created",
                 "customer.subscription.updated",
                 "customer.subscription.deleted" -> {
                stripeSubscriptionWebhookHandler.handleSubscriptionEvent(
                        event,
                        cast(stripeObject, Subscription.class)
                );
            }

            case "invoice.paid" -> {
                stripeSubscriptionWebhookHandler.handleInvoicePaid(
                        event,
                        cast(stripeObject, Invoice.class)
                );
            }

            case "invoice.payment_failed" -> {
                stripeSubscriptionWebhookHandler.handleInvoicePaymentFailed(
                        event,
                        cast(stripeObject, Invoice.class)
                );
            }

            case "payment_intent.succeeded" -> {
                stripeOneTimePaymentWebhookHandler.handlePaymentIntentSucceeded(
                        event,
                        cast(stripeObject, PaymentIntent.class)
                );
            }

            case "payment_intent.payment_failed" -> {
                stripeOneTimePaymentWebhookHandler.handlePaymentIntentFailed(
                        event,
                        cast(stripeObject, PaymentIntent.class)
                );
            }

            default -> {
                // Event is recorded and marked processed, but no business action is needed yet.
            }
        }
    }

    private <T> T cast(StripeObject stripeObject, Class<T> expectedClass) {
        if (!expectedClass.isInstance(stripeObject)) {
            throw new IllegalStateException(
                    "Expected Stripe object type " + expectedClass.getSimpleName()
                            + " but got " + stripeObject.getClass().getSimpleName()
            );
        }

        return expectedClass.cast(stripeObject);
    }

    private String extractStripeObjectId(StripeObject stripeObject) {
        if (stripeObject instanceof Subscription subscription) {
            return subscription.getId();
        }

        if (stripeObject instanceof Invoice invoice) {
            return invoice.getId();
        }

        if (stripeObject instanceof PaymentIntent paymentIntent) {
            return paymentIntent.getId();
        }

        return null;
    }
}