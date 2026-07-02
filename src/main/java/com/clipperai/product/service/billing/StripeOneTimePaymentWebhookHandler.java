package com.clipperai.product.service.billing;

import com.clipperai.product.entity.AuditAction;
import com.clipperai.product.entity.billing.BillingInvoice;
import com.clipperai.product.entity.billing.BillingInvoiceStatus;
import com.clipperai.product.entity.billing.CreditGrant;
import com.clipperai.product.entity.billing.CreditGrantStatus;
import com.clipperai.product.repository.BillingInvoiceRepository;
import com.clipperai.product.repository.CreditGrantRepository;
import com.clipperai.product.service.AuditService;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeOneTimePaymentWebhookHandler {

    private static final String PAYMENT_TYPE_EXTRA_VIDEO_CREDIT = "extra_video_credit";

    private final CreditGrantRepository creditGrantRepository;
    private final BillingInvoiceRepository billingInvoiceRepository;
    private final AuditService auditService;

    @Transactional
    public void handlePaymentIntentSucceeded(Event event, PaymentIntent paymentIntent) {

        if (!isExtraVideoCreditPayment(paymentIntent)) {

            return;
        }

        CreditGrant creditGrant = creditGrantRepository
                .findByStripePaymentIntentId(paymentIntent.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "CreditGrant not found for PaymentIntent: " + paymentIntent.getId()
                ));

        BillingInvoice billingInvoice = billingInvoiceRepository
                .findByStripePaymentIntentId(paymentIntent.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "BillingInvoice not found for PaymentIntent: " + paymentIntent.getId()
                ));
        

        if (CreditGrantStatus.ACTIVE.equals(creditGrant.getStatus())
                || CreditGrantStatus.USED.equals(creditGrant.getStatus())) {
            return;
        }
        
        

        OffsetDateTime now = OffsetDateTime.now();

        creditGrant.setStatus(CreditGrantStatus.ACTIVE);
        creditGrant.setActivatedAt(now);
        creditGrant.setUpdatedAt(now);

        int amountReceivedCents = toIntCents(
                paymentIntent.getAmountReceived() != null
                        ? paymentIntent.getAmountReceived()
                        : paymentIntent.getAmount()
        );

        billingInvoice.setStatus(BillingInvoiceStatus.PAID);
        billingInvoice.setAmountPaidCents(amountReceivedCents);
        billingInvoice.setCurrency(paymentIntent.getCurrency());
        billingInvoice.setPaidAt(now);

        if (paymentIntent.getLatestCharge() != null) {
            billingInvoice.setStripeChargeId(paymentIntent.getLatestCharge());
        }

        creditGrantRepository.save(creditGrant);
        billingInvoiceRepository.save(billingInvoice);
        
        auditService.recordWebhookAction(
                AuditAction.EXTRA_CREDIT_PAYMENT_SUCCEEDED,
                "CREDIT_GRANT",
                creditGrant.getId(),
                Map.ofEntries(
                        Map.entry("stripeEventId", event.getId()),
                        Map.entry("stripeEventType", event.getType()),
                        Map.entry("stripePaymentIntentId", paymentIntent.getId()),
                        Map.entry("creditGrantId", creditGrant.getId().toString()),
                        Map.entry("billingInvoiceId", billingInvoice.getId().toString()),
                        Map.entry("amountReceivedCents", amountReceivedCents),
                        Map.entry("currency", paymentIntent.getCurrency()),
                        Map.entry("paymentIntentStatus", paymentIntent.getStatus())
                )
        );

        auditService.recordWebhookAction(
                AuditAction.EXTRA_CREDIT_GRANTED,
                "CREDIT_GRANT",
                creditGrant.getId(),
                Map.ofEntries(
                        Map.entry("stripeEventId", event.getId()),
                        Map.entry("stripeEventType", event.getType()),
                        Map.entry("stripePaymentIntentId", paymentIntent.getId()),
                        Map.entry("creditGrantId", creditGrant.getId().toString()),
                        Map.entry("billingInvoiceId", billingInvoice.getId().toString()),
                        Map.entry("quantity", creditGrant.getQuantity()),
                        Map.entry("remainingQuantity", creditGrant.getRemainingQuantity()),
                        Map.entry("creditType", creditGrant.getCreditType().name()),
                        Map.entry("status", creditGrant.getStatus().name())
                )
        );
    }

    @Transactional
    public void handlePaymentIntentFailed(Event event, PaymentIntent paymentIntent) {
    	
        log.warn(
                "ENTERED handlePaymentIntentFailed. eventId={}, paymentIntentId={}, status={}, metadata={}",
                event.getId(),
                paymentIntent.getId(),
                paymentIntent.getStatus(),
                paymentIntent.getMetadata()
        );

        if (!isExtraVideoCreditPayment(paymentIntent)) {
        	
            return;
        }

        CreditGrant creditGrant = creditGrantRepository
                .findByStripePaymentIntentId(paymentIntent.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "CreditGrant not found for PaymentIntent: " + paymentIntent.getId()
                ));

        BillingInvoice billingInvoice = billingInvoiceRepository
                .findByStripePaymentIntentId(paymentIntent.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "BillingInvoice not found for PaymentIntent: " + paymentIntent.getId()
                ));

        if (CreditGrantStatus.ACTIVE.equals(creditGrant.getStatus())
                || CreditGrantStatus.USED.equals(creditGrant.getStatus())) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();

        creditGrant.setStatus(CreditGrantStatus.PAYMENT_FAILED);
        creditGrant.setUpdatedAt(now);

        billingInvoice.setStatus(BillingInvoiceStatus.PAYMENT_FAILED);

        creditGrantRepository.save(creditGrant);
        billingInvoiceRepository.save(billingInvoice);

        auditService.recordWebhookAction(
                AuditAction.EXTRA_CREDIT_PAYMENT_FAILED,
                "CREDIT_GRANT",
                creditGrant.getId(),
                Map.ofEntries(
                        Map.entry("stripeEventId", event.getId()),
                        Map.entry("stripeEventType", event.getType()),
                        Map.entry("stripePaymentIntentId", paymentIntent.getId()),
                        Map.entry("creditGrantId", creditGrant.getId().toString()),
                        Map.entry("billingInvoiceId", billingInvoice.getId().toString()),
                        Map.entry("currency", paymentIntent.getCurrency()),
                        Map.entry("paymentIntentStatus", paymentIntent.getStatus())
                )
        );
    }

    private boolean isExtraVideoCreditPayment(PaymentIntent paymentIntent) {
        Map<String, String> metadata = paymentIntent.getMetadata();

        if (metadata == null || metadata.isEmpty()) {
            return false;
        }

        return PAYMENT_TYPE_EXTRA_VIDEO_CREDIT.equals(metadata.get("payment_type"));
    }

    private int toIntCents(Long amount) {
        if (amount == null) {
            return 0;
        }

        return Math.toIntExact(amount);
    }
}