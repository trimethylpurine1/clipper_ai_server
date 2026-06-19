package com.clipperai.product.service;

import com.clipperai.product.entity.AuditAction;
import com.clipperai.product.entity.UserSubscription;
import com.clipperai.product.repository.UserSubscriptionRepository;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StripeSubscriptionWebhookHandler {

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final AuditService auditService;

    @Transactional
    public void handleSubscriptionEvent(Event event, Subscription stripeSubscription) {
        Optional<UserSubscription> optionalSubscription =
                userSubscriptionRepository.findByStripeSubscriptionId(stripeSubscription.getId());

        if (optionalSubscription.isEmpty()) {
            /*
             * This can happen with fake Stripe CLI trigger events.
             * For real events from your flow, you expect the local row to exist.
             */
            return;
        }

        UserSubscription localSubscription = optionalSubscription.get();

        localSubscription.setStripeStatus(stripeSubscription.getStatus());
        localSubscription.setStatus(mapStripeSubscriptionStatusToLocalStatus(stripeSubscription.getStatus()));
        localSubscription.setCancelAtPeriodEnd(Boolean.TRUE.equals(stripeSubscription.getCancelAtPeriodEnd()));

        SubscriptionItem primaryItem = getPrimarySubscriptionItem(stripeSubscription);
        localSubscription.setCurrentPeriodStart(toOffsetDateTime(primaryItem.getCurrentPeriodStart()));
        localSubscription.setCurrentPeriodEnd(toOffsetDateTime(primaryItem.getCurrentPeriodEnd()));

        userSubscriptionRepository.save(localSubscription);

        auditService.recordWebhookAction(
                AuditAction.SUBSCRIPTION_UPDATED,
                "USER_SUBSCRIPTION",
                localSubscription.getId(),
                Map.of(
                        "stripeEventId", event.getId(),
                        "stripeSubscriptionId", stripeSubscription.getId(),
                        "stripeStatus", stripeSubscription.getStatus(),
                        "localStatus", localSubscription.getStatus()
                )
        );
    }

    @Transactional
    public void handleInvoicePaid(Event event, Invoice invoice) {
        String stripeSubscriptionId = extractSubscriptionIdFromInvoice(invoice);

        if (stripeSubscriptionId == null || stripeSubscriptionId.isBlank()) {
            return;
        }

        Optional<UserSubscription> optionalSubscription =
                userSubscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId);

        if (optionalSubscription.isEmpty()) {
            return;
        }

        UserSubscription localSubscription = optionalSubscription.get();

        localSubscription.setStatus("active");
        localSubscription.setStripeStatus("active");

        userSubscriptionRepository.save(localSubscription);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("stripeEventId", event.getId());
        metadata.put("stripeInvoiceId", invoice.getId());
        metadata.put("stripeSubscriptionId", stripeSubscriptionId);
        metadata.put("amountPaidCents", invoice.getAmountPaid());
        metadata.put("currency", invoice.getCurrency());
        metadata.put("invoiceStatus", invoice.getStatus());

        auditService.recordWebhookAction(
                AuditAction.INVOICE_PAID,
                "USER_SUBSCRIPTION",
                localSubscription.getId(),
                metadata
        );
    }

    @Transactional
    public void handleInvoicePaymentFailed(Event event, Invoice invoice) {
        String stripeSubscriptionId = extractSubscriptionIdFromInvoice(invoice);

        if (stripeSubscriptionId == null || stripeSubscriptionId.isBlank()) {
            return;
        }

        Optional<UserSubscription> optionalSubscription =
                userSubscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId);

        if (optionalSubscription.isEmpty()) {
            return;
        }

        UserSubscription localSubscription = optionalSubscription.get();

        localSubscription.setStatus("payment_failed");
        localSubscription.setStripeStatus("past_due");

        userSubscriptionRepository.save(localSubscription);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("stripeEventId", event.getId());
        metadata.put("stripeInvoiceId", invoice.getId());
        metadata.put("stripeSubscriptionId", stripeSubscriptionId);
        metadata.put("amountDueCents", invoice.getAmountDue());
        metadata.put("currency", invoice.getCurrency());
        metadata.put("invoiceStatus", invoice.getStatus());

        auditService.recordWebhookAction(
                AuditAction.INVOICE_PAYMENT_FAILED,
                "USER_SUBSCRIPTION",
                localSubscription.getId(),
                metadata
        );
    }

    private String mapStripeSubscriptionStatusToLocalStatus(String stripeStatus) {
        if (stripeStatus == null) {
            return "inactive";
        }

        return switch (stripeStatus) {
            case "active", "trialing" -> "active";
            case "incomplete" -> "pending_payment";
            case "past_due" -> "past_due";
            case "canceled" -> "canceled";
            case "unpaid" -> "payment_failed";
            case "incomplete_expired" -> "expired";
            default -> "inactive";
        };
    }

    private String extractSubscriptionIdFromInvoice(Invoice invoice) {
        /*
         * Newer Stripe API versions store the subscription reference under:
         * invoice.parent.subscription_details.subscription
         */
        if (invoice.getParent() != null
                && invoice.getParent().getSubscriptionDetails() != null
                && invoice.getParent().getSubscriptionDetails().getSubscription() != null) {
            return invoice.getParent().getSubscriptionDetails().getSubscription();
        }

        /*
         * Fallback: if you later put subscription IDs in invoice metadata,
         * this lets the handler still work.
         */
        if (invoice.getMetadata() != null) {
            return invoice.getMetadata().get("stripeSubscriptionId");
        }

        return null;
    }

    private SubscriptionItem getPrimarySubscriptionItem(Subscription stripeSubscription) {
        if (stripeSubscription.getItems() == null
                || stripeSubscription.getItems().getData() == null
                || stripeSubscription.getItems().getData().isEmpty()) {
            throw new IllegalStateException("Stripe subscription did not include subscription items.");
        }

        return stripeSubscription.getItems().getData().get(0);
    }

    private OffsetDateTime toOffsetDateTime(Long epochSeconds) {
        if (epochSeconds == null) {
            return null;
        }

        return OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(epochSeconds),
                ZoneOffset.UTC
        );
    }
}