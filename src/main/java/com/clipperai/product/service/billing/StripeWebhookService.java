package com.clipperai.product.service.billing;

import com.clipperai.product.repository.UserSubscriptionRepository;
import com.clipperai.product.service.AuditService;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.*;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import com.clipperai.product.entity.*;
import com.clipperai.product.entity.billing.StripeWebhookEvent;
import com.clipperai.product.entity.billing.UserSubscription;

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.stripe.*;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    private final StripeWebhookEventService stripeWebhookEventService;
    private final StripeSubscriptionWebhookHandler stripeSubscriptionWebhookHandler;
    private final StripeOneTimePaymentWebhookHandler stripeOneTimePaymentWebhookHandler;
    private final AuditService auditService;
    private final UserSubscriptionRepository userSubscriptionRepository;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    public void handleWebhook(String payload, String signatureHeader)
            throws SignatureVerificationException {

        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("Stripe webhook secret is not configured.");
        }

        Event event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);

        StripeObject stripeObject = deserializeStripeObject(event);

        String stripeObjectId = extractStripeObjectId(stripeObject);

        StripeWebhookEvent webhookEvent =
                stripeWebhookEventService.recordReceivedOrGetExisting(
                        event.getId(),
                        event.getType(),
                        stripeObjectId
                );

        if ("processed".equalsIgnoreCase(webhookEvent.getStatus())) {
            log.info(
                    "Stripe webhook already processed. eventId={}, eventType={}",
                    event.getId(),
                    event.getType()
            );
            return;
        }

        try {
            log.info(
                    "Dispatching Stripe webhook. eventId={}, eventType={}, stripeObjectId={}",
                    event.getId(),
                    event.getType(),
                    stripeObjectId
            );

            dispatch(event, stripeObject);
            
            recordAuditIfImportant(event, stripeObject, stripeObjectId);

            stripeWebhookEventService.markProcessed(event.getId());

            log.info(
                    "Stripe webhook processed successfully. eventId={}, eventType={}",
                    event.getId(),
                    event.getType()
            );

        } catch (Exception processingFailed) {
            stripeWebhookEventService.markFailed(event.getId(), processingFailed);

            log.error(
                    "Stripe webhook processing failed. eventId={}, eventType={}",
                    event.getId(),
                    event.getType(),
                    processingFailed
            );

            throw processingFailed;
        }
    }
    
    private StripeObject deserializeStripeObject(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

        log.info(
                "Attempting safe Stripe webhook deserialization. eventId={}, eventType={}, eventApiVersion={}, stripeJavaApiVersion={}",
                event.getId(),
                event.getType(),
                event.getApiVersion(),
                Stripe.API_VERSION
        );

        return deserializer.getObject()
                .map(stripeObject -> {
                    log.info(
                            "Stripe webhook safe deserialization succeeded. eventId={}, eventType={}, objectClass={}",
                            event.getId(),
                            event.getType(),
                            stripeObject.getClass().getSimpleName()
                    );

                    return stripeObject;
                })
                .orElseGet(() -> {
                    log.warn(
                            "Stripe webhook safe deserialization failed. Falling back to unsafe deserialization. eventId={}, eventType={}, eventApiVersion={}, stripeJavaApiVersion={}",
                            event.getId(),
                            event.getType(),
                            event.getApiVersion(),
                            Stripe.API_VERSION
                    );

                    try {
                        StripeObject unsafeObject = deserializer.deserializeUnsafe();

                        log.warn(
                                "Stripe webhook unsafe deserialization succeeded. eventId={}, eventType={}, objectClass={}",
                                event.getId(),
                                event.getType(),
                                unsafeObject.getClass().getSimpleName()
                        );

                        return unsafeObject;

                    } catch (Exception ex) {
                        log.error(
                                "Stripe webhook unsafe deserialization failed. eventId={}, eventType={}, eventApiVersion={}, stripeJavaApiVersion={}",
                                event.getId(),
                                event.getType(),
                                event.getApiVersion(),
                                Stripe.API_VERSION,
                                ex
                        );

                        throw new IllegalStateException(
                                "Unable to deserialize Stripe webhook object for event "
                                        + event.getId()
                                        + " of type "
                                        + event.getType()
                                        + ". Event API version: "
                                        + event.getApiVersion()
                                        + ". stripe-java API version: "
                                        + Stripe.API_VERSION,
                                ex
                        );
                    }
                });
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
    
    private void recordAuditIfImportant(Event event, StripeObject stripeObject, String stripeObjectId) {
        Optional<WebhookAuditDetails> auditDetails =
                buildWebhookAuditDetails(event, stripeObject, stripeObjectId);

        if (auditDetails.isEmpty()) {
            log.info(
                    "Stripe webhook does not require audit log. eventId={}, eventType={}",
                    event.getId(),
                    event.getType()
            );
            return;
        }

        WebhookAuditDetails details = auditDetails.get();

        auditService.recordWebhookAction(
                details.action(),
                details.targetType(),
                details.targetId(),
                details.metadata()
        );

        log.info(
                "Stripe webhook audit log recorded. eventId={}, eventType={}, action={}, targetType={}, targetId={}",
                event.getId(),
                event.getType(),
                details.action(),
                details.targetType(),
                details.targetId()
        );
    }
    
    private Optional<WebhookAuditDetails> buildWebhookAuditDetails(
            Event event,
            StripeObject stripeObject,
            String stripeObjectId
    ) {
        String eventType = event.getType();

        return switch (eventType) {
            case "customer.subscription.created",
                 "customer.subscription.updated",
                 "customer.subscription.deleted" ->
                    buildSubscriptionAuditDetails(event, cast(stripeObject, Subscription.class));

            case "invoice.paid",
                 "invoice.payment_failed" ->
                    buildInvoiceAuditDetails(event, cast(stripeObject, Invoice.class));

            case "payment_intent.succeeded",
                 "payment_intent.payment_failed" ->
                    buildPaymentIntentAuditDetails(event, cast(stripeObject, PaymentIntent.class));

            default -> Optional.empty();
        };
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
    
    private Map<String, Object> baseWebhookMetadata(Event event) {
        Map<String, Object> metadata = new HashMap<>();

        putIfPresent(metadata, "stripeEventId", event.getId());
        putIfPresent(metadata, "stripeEventType", event.getType());
        putIfPresent(metadata, "stripeEventApiVersion", event.getApiVersion());

        return metadata;
    }
    
    private Optional<WebhookAuditDetails> buildInvoiceAuditDetails(
            Event event,
            Invoice invoice
    ) {
        AuditAction action = switch (event.getType()) {
            case "invoice.paid" -> AuditAction.INVOICE_PAID;
            case "invoice.payment_failed" -> AuditAction.INVOICE_PAYMENT_FAILED;
            default -> null;
        };

        if (action == null) {
            return Optional.empty();
        }

        String stripeSubscriptionId = extractSubscriptionIdFromInvoice(invoice);

        UserSubscription localSubscription = null;

        if (stripeSubscriptionId != null && !stripeSubscriptionId.isBlank()) {
            localSubscription = userSubscriptionRepository
                    .findByStripeSubscriptionId(stripeSubscriptionId)
                    .orElse(null);
        }

        Map<String, Object> metadata = baseWebhookMetadata(event);
        putIfPresent(metadata, "stripeInvoiceId", invoice.getId());
        putIfPresent(metadata, "stripeSubscriptionId", stripeSubscriptionId);
        putIfPresent(metadata, "invoiceStatus", invoice.getStatus());
        putIfPresent(metadata, "amountPaidCents", invoice.getAmountPaid());
        putIfPresent(metadata, "amountDueCents", invoice.getAmountDue());
        putIfPresent(metadata, "currency", invoice.getCurrency());

        UUID targetId = localSubscription == null ? null : localSubscription.getId();

        putIfPresent(metadata, "localSubscriptionFound", localSubscription != null);
        if (localSubscription != null) {
            putIfPresent(metadata, "localStatus", localSubscription.getStatus());
            putIfPresent(metadata, "userId", localSubscription.getUser().getId());
        }

        return Optional.of(new WebhookAuditDetails(
                action,
                "USER_SUBSCRIPTION",
                targetId,
                metadata
        ));
    }
    
    private Optional<WebhookAuditDetails> buildSubscriptionAuditDetails(
            Event event,
            Subscription subscription
    ) {
        AuditAction action = switch (event.getType()) {
            case "customer.subscription.created" -> AuditAction.SUBSCRIPTION_CREATED;
            case "customer.subscription.updated" -> AuditAction.SUBSCRIPTION_UPDATED;
            case "customer.subscription.deleted" -> AuditAction.SUBSCRIPTION_DELETED;
            default -> null;
        };

        if (action == null) {
            return Optional.empty();
        }

        UserSubscription localSubscription =
                userSubscriptionRepository.findByStripeSubscriptionId(subscription.getId())
                        .orElse(null);

        Map<String, Object> metadata = baseWebhookMetadata(event);
        putIfPresent(metadata, "stripeSubscriptionId", subscription.getId());
        putIfPresent(metadata, "stripeStatus", subscription.getStatus());
        putIfPresent(metadata, "cancelAtPeriodEnd", subscription.getCancelAtPeriodEnd());

        UUID targetId = localSubscription == null ? null : localSubscription.getId();

        putIfPresent(metadata, "localSubscriptionFound", localSubscription != null);
        if (localSubscription != null) {
            putIfPresent(metadata, "localStatus", localSubscription.getStatus());
            putIfPresent(metadata, "userId", localSubscription.getUser().getId());
        }

        return Optional.of(new WebhookAuditDetails(
                action,
                "USER_SUBSCRIPTION",
                targetId,
                metadata
        ));
    }
    
    private String extractSubscriptionIdFromInvoice(Invoice invoice) {
        if (invoice.getParent() != null
                && invoice.getParent().getSubscriptionDetails() != null
                && invoice.getParent().getSubscriptionDetails().getSubscription() != null) {
            return invoice.getParent().getSubscriptionDetails().getSubscription();
        }

        if (invoice.getMetadata() != null) {
            return invoice.getMetadata().get("stripeSubscriptionId");
        }

        return null;
    }
    
    private Optional<WebhookAuditDetails> buildPaymentIntentAuditDetails(
            Event event,
            PaymentIntent paymentIntent
    ) {
        Map<String, String> stripeMetadata = paymentIntent.getMetadata();

        if (stripeMetadata == null || stripeMetadata.isEmpty()) {
            return Optional.empty();
        }

        String paymentType = stripeMetadata.get("payment_type");

        if (!"extra_video_credit".equals(paymentType)) {
            return Optional.empty();
        }

        AuditAction action = switch (event.getType()) {
            case "payment_intent.succeeded" -> AuditAction.EXTRA_CREDIT_PAYMENT_SUCCEEDED;
            case "payment_intent.payment_failed" -> AuditAction.EXTRA_CREDIT_PAYMENT_FAILED;
            default -> null;
        };

        if (action == null) {
            return Optional.empty();
        }

        Map<String, Object> metadata = baseWebhookMetadata(event);
        putIfPresent(metadata, "stripePaymentIntentId", paymentIntent.getId());
        putIfPresent(metadata, "paymentType", paymentType);
        putIfPresent(metadata, "amountCents", paymentIntent.getAmount());
        putIfPresent(metadata, "currency", paymentIntent.getCurrency());
        putIfPresent(metadata, "paymentIntentStatus", paymentIntent.getStatus());
        putIfPresent(metadata, "appUserId", stripeMetadata.get("app_user_id"));
        putIfPresent(metadata, "billingCustomerId", stripeMetadata.get("billing_customer_id"));
        putIfPresent(metadata, "creditGrantId", stripeMetadata.get("credit_grant_id"));

        UUID targetId = parseUuidOrNull(stripeMetadata.get("credit_grant_id"));

        return Optional.of(new WebhookAuditDetails(
                action,
                "CREDIT_GRANT",
                targetId,
                metadata
        ));
    }
    
    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }
    
    private UUID parseUuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private record WebhookAuditDetails(
            AuditAction action,
            String targetType,
            UUID targetId,
            Map<String, Object> metadata
    ) {
    }
}

