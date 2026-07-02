package com.clipperai.product.service.billing;

import com.clipperai.product.dto.billing.StartExtraCreditPurchaseRequest;
import com.clipperai.product.dto.billing.StartExtraCreditPurchaseResponse;
import com.clipperai.product.entity.AppUser;
import com.clipperai.product.entity.AuditAction;
import com.clipperai.product.entity.billing.BillingCustomer;
import com.clipperai.product.entity.billing.BillingInvoice;
import com.clipperai.product.entity.billing.BillingInvoiceStatus;
import com.clipperai.product.entity.billing.CreditGrant;
import com.clipperai.product.entity.billing.CreditGrantStatus;
import com.clipperai.product.entity.billing.CreditType;
import com.clipperai.product.entity.billing.OneTimeCreditProduct;
import com.clipperai.product.repository.BillingInvoiceRepository;
import com.clipperai.product.repository.CreditGrantRepository;
import com.clipperai.product.repository.OneTimeCreditProductRepository;
import com.clipperai.product.service.AuditService;
import com.clipperai.product.service.CurrentUserService;
import com.clipperai.product.service.RequestInfoService;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExtraVideoCreditPurchaseService {
	
	private final RequestInfoService requestInfoService;
	
    private static final String EXTRA_VIDEO_CREDIT_PRODUCT_CODE = "extra_video_credit";
    private static final String PAYMENT_TYPE_EXTRA_VIDEO_CREDIT = "extra_video_credit";
    private static final String BILLING_REASON_EXTRA_VIDEO_CREDIT = "extra_video_credit";

    private final CurrentUserService currentUserService;
    private final BillingCustomerService billingCustomerService;

    private final OneTimeCreditProductRepository oneTimeCreditProductRepository;
    private final CreditGrantRepository creditGrantRepository;
    private final BillingInvoiceRepository billingInvoiceRepository;

    private final AuditService auditService;

    @Value("${stripe.publishable-key}")
    private String stripePublishableKey;

    @Transactional
    public StartExtraCreditPurchaseResponse startPurchase(
            StartExtraCreditPurchaseRequest request, HttpServletRequest infoRequest
    ) throws StripeException {
    	
    	String ipAddress = requestInfoService.getClientIp(infoRequest);
        String userAgent = requestInfoService.getUserAgent(infoRequest);


        AppUser currentUser = currentUserService.getCurrentUser();

        ensureUserCanPurchaseExtraCredit(currentUser);

        int requestedQuantity = request.quantity();

        OneTimeCreditProduct product = oneTimeCreditProductRepository
                .findByCodeAndActiveTrue(EXTRA_VIDEO_CREDIT_PRODUCT_CODE)
                .orElseThrow(() -> new IllegalStateException(
                        "Active extra video credit product is not configured."
                ));

        validateProduct(product);

        BillingCustomer billingCustomer =
                billingCustomerService.getOrCreateForUser(currentUser, infoRequest);

        int creditsGranted = Math.multiplyExact(
                product.getCreditQuantity(),
                requestedQuantity
        );

        int amountDueCents = Math.multiplyExact(
                product.getUnitAmountCents(),
                requestedQuantity
        );

        OffsetDateTime now = OffsetDateTime.now();

        CreditGrant creditGrant = CreditGrant.builder()
                .user(currentUser)
                .billingCustomer(billingCustomer)
                .oneTimeCreditProduct(product)
                .creditType(CreditType.VIDEO_UPLOAD)
                .quantity(creditsGranted)
                .remainingQuantity(creditsGranted)
                .amountCents(amountDueCents)
                .currency(product.getCurrency())
                .status(CreditGrantStatus.PENDING_PAYMENT)
                .reason("extra_video_credit_purchase")
                .createdAt(now)
                .updatedAt(now)
                .build();

        creditGrant = creditGrantRepository.save(creditGrant);

        BillingInvoice billingInvoice = BillingInvoice.builder()
                .user(currentUser)
                .stripeInvoiceId(null)
                .stripePaymentIntentId(null)
                .stripeChargeId(null)
                .amountPaidCents(0)
                .amountRefundedCents(0)
                .currency(product.getCurrency())
                .status(BillingInvoiceStatus.PAYMENT_PENDING)
                .reason(BILLING_REASON_EXTRA_VIDEO_CREDIT)
                .commissionable(false)
                .commissionBasisCents(0)
                .refundStatus("none")
                .createdAt(now)
                .build();

        billingInvoice = billingInvoiceRepository.save(billingInvoice);

        creditGrant.setBillingInvoice(billingInvoice);

        PaymentIntent paymentIntent = createStripePaymentIntent(
                currentUser,
                billingCustomer,
                product,
                creditGrant,
                billingInvoice,
                requestedQuantity,
                creditsGranted,
                amountDueCents
        );

        creditGrant.setStripePaymentIntentId(paymentIntent.getId());
        billingInvoice.setStripePaymentIntentId(paymentIntent.getId());

        creditGrantRepository.save(creditGrant);
        billingInvoiceRepository.save(billingInvoice);

        auditService.recordUserAction(
                currentUser,
                AuditAction.EXTRA_CREDIT_PURCHASE_STARTED,
                "CREDIT_GRANT",
                creditGrant.getId(),
                ipAddress,
                userAgent,
                Map.ofEntries(
                        Map.entry("creditGrantId", creditGrant.getId().toString()),
                        Map.entry("billingInvoiceId", billingInvoice.getId().toString()),
                        Map.entry("stripePaymentIntentId", paymentIntent.getId()),
                        Map.entry("oneTimeCreditProductId", product.getId().toString()),
                        Map.entry("stripeProductId", product.getStripeProductId()),
                        Map.entry("stripePriceId", product.getStripePriceId()),
                        Map.entry("quantity", requestedQuantity),
                        Map.entry("creditsGranted", creditsGranted),
                        Map.entry("amountDueCents", amountDueCents),
                        Map.entry("currency", product.getCurrency()),
                        Map.entry("creditType", CreditType.VIDEO_UPLOAD.name()),
                        Map.entry("creditStatus", CreditGrantStatus.PENDING_PAYMENT.name()),
                        Map.entry("billingInvoiceStatus", BillingInvoiceStatus.PAYMENT_PENDING.name())
                )
        );

        return new StartExtraCreditPurchaseResponse(
                paymentIntent.getClientSecret(),
                stripePublishableKey,
                creditGrant.getId(),
                billingInvoice.getId(),
                requestedQuantity,
                creditsGranted,
                amountDueCents,
                product.getCurrency(),
                CreditGrantStatus.PENDING_PAYMENT.name()
        );
    }

    private PaymentIntent createStripePaymentIntent(
            AppUser currentUser,
            BillingCustomer billingCustomer,
            OneTimeCreditProduct product,
            CreditGrant creditGrant,
            BillingInvoice billingInvoice,
            int requestedQuantity,
            int creditsGranted,
            int amountDueCents
    ) throws StripeException {

        Map<String, String> metadata = new HashMap<>();

        metadata.put("payment_type", PAYMENT_TYPE_EXTRA_VIDEO_CREDIT);
        metadata.put("app_user_id", currentUser.getId());
        metadata.put("billing_customer_id", billingCustomer.getId().toString());
        metadata.put("credit_grant_id", creditGrant.getId().toString());
        metadata.put("billing_invoice_id", billingInvoice.getId().toString());
        metadata.put("one_time_credit_product_id", product.getId().toString());
        metadata.put("stripe_product_id", product.getStripeProductId());
        metadata.put("stripe_price_id", product.getStripePriceId());
        metadata.put("quantity", String.valueOf(requestedQuantity));
        metadata.put("credits_granted", String.valueOf(creditsGranted));
        metadata.put("amount_due_cents", String.valueOf(amountDueCents));
        metadata.put("credit_type", CreditType.VIDEO_UPLOAD.name());
        metadata.put("credit_status", CreditGrantStatus.PENDING_PAYMENT.name());
        metadata.put("billing_invoice_status", BillingInvoiceStatus.PAYMENT_PENDING.name());

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount((long) amountDueCents)
                .setCurrency(product.getCurrency())
                .setCustomer(billingCustomer.getStripeCustomerId())
                .setDescription("ClipperAI extra video credit x " + requestedQuantity)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build()
                )
                .putAllMetadata(metadata)
                .build();

        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey("extra-video-credit-" + creditGrant.getId())
                .build();

        return PaymentIntent.create(params, requestOptions);
    }

    private void validateProduct(OneTimeCreditProduct product) {
        if (product.getStripeProductId() == null || product.getStripeProductId().isBlank()) {
            throw new IllegalStateException("Extra video credit Stripe product ID is missing.");
        }

        if (product.getStripePriceId() == null || product.getStripePriceId().isBlank()) {
            throw new IllegalStateException("Extra video credit Stripe price ID is missing.");
        }

        if (product.getUnitAmountCents() == null || product.getUnitAmountCents() <= 0) {
            throw new IllegalStateException("Extra video credit amount must be positive.");
        }

        if (product.getCreditQuantity() == null || product.getCreditQuantity() <= 0) {
            throw new IllegalStateException("Extra video credit quantity must be positive.");
        }

        if (product.getCurrency() == null || product.getCurrency().isBlank()) {
            throw new IllegalStateException("Extra video credit currency is missing.");
        }

        if (!CreditType.VIDEO_UPLOAD.equals(product.getCreditType())) {
            throw new IllegalStateException("Extra video credit product has invalid credit type.");
        }
    }

    private void ensureUserCanPurchaseExtraCredit(AppUser currentUser) {
        if (!currentUser.isEmailVerified()) {
            throw new IllegalStateException("Email must be verified before purchasing extra credits.");
        }
    }
}