package com.clipperai.product.service.billing;

import com.clipperai.product.dto.billing.StartSubscriptionRequest;
import com.clipperai.product.dto.billing.StartSubscriptionResponse;
import com.clipperai.product.entity.AppUser;
import com.clipperai.product.dto.billing.CancelSubscriptionResponse;
import com.clipperai.product.entity.AuditAction;
import com.clipperai.product.entity.billing.BillingCustomer;
import com.clipperai.product.entity.billing.SubscriptionPlan;
import com.clipperai.product.entity.billing.UserSubscription;
import com.clipperai.product.repository.UserSubscriptionRepository;
import com.clipperai.product.service.AuditService;
import com.clipperai.product.service.CurrentUserService;
import com.clipperai.product.service.RequestInfoService;
import com.stripe.exception.StripeException;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Subscription;
import com.stripe.net.RequestOptions;
import com.stripe.param.SubscriptionUpdateParams;
import lombok.extern.slf4j.Slf4j;

import java.util.List;


import com.stripe.param.SubscriptionCreateParams;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stripe.model.SubscriptionItem;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionBillingService {

    private final CurrentUserService currentUserService;
    private final BillingCustomerService billingCustomerService;
    private final BillingPlanService billingPlanService;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final AuditService auditService;
    private final RequestInfoService requestInfoService;

    @Transactional
    public StartSubscriptionResponse startSubscription(
            StartSubscriptionRequest requestDto,
            HttpServletRequest request
    ) throws StripeException {

        AppUser currentUser = currentUserService.getCurrentUser();

        if (!currentUser.isEmailVerified()) {
            throw new IllegalStateException("Please verify your email before starting a subscription.");
        }

        preventDuplicateActiveOrPendingSubscription(currentUser);

        SubscriptionPlan plan = billingPlanService.getActivePlanOrThrow(requestDto.planId());

        BillingCustomer billingCustomer =
                billingCustomerService.getOrCreateForCurrentUser(request);

        Subscription stripeSubscription = createStripeSubscription(
                currentUser,
                billingCustomer,
                plan
        );

        String clientSecret = extractClientSecret(stripeSubscription);

        UserSubscription localSubscription = saveLocalPendingSubscription(
                currentUser,
                billingCustomer,
                plan,
                stripeSubscription
        );

        auditSubscriptionStarted(
                currentUser,
                localSubscription,
                billingCustomer,
                plan,
                stripeSubscription,
                request
        );

        return new StartSubscriptionResponse(
                localSubscription.getId(),
                plan.getId(),
                stripeSubscription.getId(),
                clientSecret,
                stripeSubscription.getStatus(),
                localSubscription.getStatus()
        );
    }
    
    
    @Transactional
    public CancelSubscriptionResponse cancelCurrentSubscriptionAtPeriodEnd(
            HttpServletRequest request
    ) throws StripeException {

        String ipAddress = requestInfoService.getClientIp(request);
        String userAgent = requestInfoService.getUserAgent(request);

        AppUser currentUser = currentUserService.getCurrentUser();

        List<String> cancellableStatuses = List.of(
                "active",
                "past_due",
                "pending_payment"
        );

        UserSubscription localSubscription = userSubscriptionRepository
                .findTopByUserAndStatusInOrderByCreatedAtDesc(currentUser, cancellableStatuses)
                .orElseThrow(() -> new IllegalStateException(
                        "No active or pending subscription found for this user."
                ));

        if (localSubscription.getStripeSubscriptionId() == null
                || localSubscription.getStripeSubscriptionId().isBlank()) {
            throw new IllegalStateException(
                    "Local subscription does not have a Stripe subscription ID."
            );
        }

        log.info(
                "User requested subscription cancellation. userId={}, localSubscriptionId={}, stripeSubscriptionId={}, ipAddress={}",
                currentUser.getId(),
                localSubscription.getId(),
                localSubscription.getStripeSubscriptionId(),
                ipAddress
        );

        if (Boolean.TRUE.equals(localSubscription.getCancelAtPeriodEnd())) {
            log.info(
                    "Subscription is already scheduled to cancel at period end. userId={}, localSubscriptionId={}, stripeSubscriptionId={}",
                    currentUser.getId(),
                    localSubscription.getId(),
                    localSubscription.getStripeSubscriptionId()
            );

            return new CancelSubscriptionResponse(
                    localSubscription.getId(),
                    localSubscription.getStripeSubscriptionId(),
                    localSubscription.getStripeStatus(),
                    localSubscription.getStatus(),
                    localSubscription.getCancelAtPeriodEnd(),
                    localSubscription.getCurrentPeriodEnd(),
                    "Subscription is already scheduled to cancel at the end of the current billing period."
            );
        }

        String previousLocalStatus = localSubscription.getStatus();
        String previousStripeStatus = localSubscription.getStripeStatus();
        Boolean previousCancelAtPeriodEnd = localSubscription.getCancelAtPeriodEnd();

        Subscription stripeSubscription;

        try {
            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .setCancelAtPeriodEnd(true)
                    .build();

            RequestOptions requestOptions = RequestOptions.builder()
                    .setIdempotencyKey("cancel-subscription-at-period-end-" + localSubscription.getId())
                    .build();

            stripeSubscription = Subscription.retrieve(localSubscription.getStripeSubscriptionId())
                    .update(params, requestOptions);

        } catch (StripeException stripeException) {
            log.error(
                    "Stripe subscription cancellation request failed. userId={}, localSubscriptionId={}, stripeSubscriptionId={}",
                    currentUser.getId(),
                    localSubscription.getId(),
                    localSubscription.getStripeSubscriptionId(),
                    stripeException
            );

            throw stripeException;
        }

        SubscriptionItem primaryItem = getPrimarySubscriptionItem(stripeSubscription);

        localSubscription.setStripeStatus(stripeSubscription.getStatus());
        localSubscription.setStatus(mapStripeSubscriptionStatusToLocalStatus(stripeSubscription.getStatus()));
        localSubscription.setCancelAtPeriodEnd(Boolean.TRUE.equals(stripeSubscription.getCancelAtPeriodEnd()));
        localSubscription.setCurrentPeriodStart(toOffsetDateTime(primaryItem.getCurrentPeriodStart()));
        localSubscription.setCurrentPeriodEnd(toOffsetDateTime(primaryItem.getCurrentPeriodEnd()));

        UserSubscription savedSubscription = userSubscriptionRepository.save(localSubscription);

        auditService.recordUserAction(
                currentUser,
                AuditAction.SUBSCRIPTION_CANCELED,
                "USER_SUBSCRIPTION",
                savedSubscription.getId(),
                ipAddress,
                userAgent,
                Map.ofEntries(
                        Map.entry("cancelMode", "AT_PERIOD_END"),
                        Map.entry("stripeSubscriptionId", savedSubscription.getStripeSubscriptionId()),
                        Map.entry("previousLocalStatus", previousLocalStatus == null ? "" : previousLocalStatus),
                        Map.entry("previousStripeStatus", previousStripeStatus == null ? "" : previousStripeStatus),
                        Map.entry("previousCancelAtPeriodEnd", previousCancelAtPeriodEnd),
                        Map.entry("newLocalStatus", savedSubscription.getStatus()),
                        Map.entry("newStripeStatus", savedSubscription.getStripeStatus()),
                        Map.entry("cancelAtPeriodEnd", savedSubscription.getCancelAtPeriodEnd()),
                        Map.entry("currentPeriodStart", savedSubscription.getCurrentPeriodStart() == null ? "" : savedSubscription.getCurrentPeriodStart().toString()),
                        Map.entry("currentPeriodEnd", savedSubscription.getCurrentPeriodEnd() == null ? "" : savedSubscription.getCurrentPeriodEnd().toString()),
                        Map.entry("planId", savedSubscription.getPlan() == null ? "" : savedSubscription.getPlan().getId().toString()),
                        Map.entry("planName", savedSubscription.getPlan() == null ? "" : savedSubscription.getPlan().getName())
                )
        );

        log.info(
                "Subscription scheduled for cancellation at period end. userId={}, localSubscriptionId={}, stripeSubscriptionId={}, currentPeriodEnd={}",
                currentUser.getId(),
                savedSubscription.getId(),
                savedSubscription.getStripeSubscriptionId(),
                savedSubscription.getCurrentPeriodEnd()
        );

        return new CancelSubscriptionResponse(
                savedSubscription.getId(),
                savedSubscription.getStripeSubscriptionId(),
                savedSubscription.getStripeStatus(),
                savedSubscription.getStatus(),
                savedSubscription.getCancelAtPeriodEnd(),
                savedSubscription.getCurrentPeriodEnd(),
                "Subscription will cancel at the end of the current billing period."
        );
    }

    private void preventDuplicateActiveOrPendingSubscription(AppUser currentUser) {
        userSubscriptionRepository.findTopByUserOrderByCreatedAtDesc(currentUser)
                .ifPresent(existingSubscription -> {
                    String status = existingSubscription.getStatus();

                    boolean alreadyActiveOrPending =
                            "active".equalsIgnoreCase(status)
                                    || "pending_payment".equalsIgnoreCase(status);

                    if (alreadyActiveOrPending) {
                        throw new IllegalStateException(
                                "You already have an active or pending subscription."
                        );
                    }
                });
    }

    private Subscription createStripeSubscription(
            AppUser currentUser,
            BillingCustomer billingCustomer,
            SubscriptionPlan plan
    ) throws StripeException {

        SubscriptionCreateParams params = SubscriptionCreateParams.builder()
                .setCustomer(billingCustomer.getStripeCustomerId())
                .addItem(
                        SubscriptionCreateParams.Item.builder()
                                .setPrice(plan.getStripePriceId())
                                .build()
                )
                .setPaymentBehavior(SubscriptionCreateParams.PaymentBehavior.DEFAULT_INCOMPLETE)
                .setPaymentSettings(
                        SubscriptionCreateParams.PaymentSettings.builder()
                                .setSaveDefaultPaymentMethod(
                                        SubscriptionCreateParams.PaymentSettings.SaveDefaultPaymentMethod.ON_SUBSCRIPTION
                                )
                                .build()
                )
                .putMetadata("app_user_id", currentUser.getId())
                .putMetadata("billing_customer_id", billingCustomer.getId().toString())
                .putMetadata("plan_id", plan.getId().toString())
                .addExpand("latest_invoice.confirmation_secret")
                .build();

        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey("start-subscription-" + currentUser.getId() + "-" + plan.getId())
                .build();

        return Subscription.create(params, requestOptions);
    }

    private UserSubscription saveLocalPendingSubscription(
            AppUser currentUser,
            BillingCustomer billingCustomer,
            SubscriptionPlan plan,
            Subscription stripeSubscription
    ) {
        SubscriptionItem primaryItem = getPrimarySubscriptionItem(stripeSubscription);

        UserSubscription localSubscription = UserSubscription.builder()
                .user(currentUser)
                .billingCustomer(billingCustomer)
                .plan(plan)
                .stripeSubscriptionId(stripeSubscription.getId())
                .stripeStatus(stripeSubscription.getStatus())
                .status("pending_payment")
                .currentPeriodStart(toOffsetDateTime(primaryItem.getCurrentPeriodStart()))
                .currentPeriodEnd(toOffsetDateTime(primaryItem.getCurrentPeriodEnd()))
                .cancelAtPeriodEnd(Boolean.TRUE.equals(stripeSubscription.getCancelAtPeriodEnd()))
                .build();

        return userSubscriptionRepository.save(localSubscription);
    }
    

    private void auditSubscriptionStarted(
            AppUser currentUser,
            UserSubscription localSubscription,
            BillingCustomer billingCustomer,
            SubscriptionPlan plan,
            Subscription stripeSubscription,
            HttpServletRequest request
    ) {
        String ipAddress = requestInfoService.getClientIp(request);
        String userAgent = requestInfoService.getUserAgent(request);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("stripeSubscriptionId", stripeSubscription.getId());
        metadata.put("stripeCustomerId", billingCustomer.getStripeCustomerId());
        metadata.put("billingCustomerId", billingCustomer.getId().toString());
        metadata.put("planId", plan.getId().toString());
        metadata.put("planName", plan.getName());
        metadata.put("stripePriceId", plan.getStripePriceId());
        metadata.put("monthlyPriceCents", plan.getMonthlyPriceCents());
        metadata.put("currency", plan.getCurrency());
        metadata.put("stripeStatus", stripeSubscription.getStatus());
        metadata.put("localStatus", localSubscription.getStatus());

        auditService.recordUserAction(
                currentUser,
                AuditAction.SUBSCRIPTION_CREATED,
                "USER_SUBSCRIPTION",
                localSubscription.getId(),
                ipAddress,
                userAgent,
                metadata
        );
    }

    private String extractClientSecret(Subscription stripeSubscription) {
        Invoice latestInvoice = stripeSubscription.getLatestInvoiceObject();

        if (latestInvoice == null) {
            throw new IllegalStateException("Stripe subscription did not include latest invoice.");
        }

        if (latestInvoice.getConfirmationSecret() == null) {
            throw new IllegalStateException("Stripe latest invoice did not include confirmation secret.");
        }

        String clientSecret = latestInvoice.getConfirmationSecret().getClientSecret();

        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("Stripe invoice confirmation secret did not include client secret.");
        }

        return clientSecret;
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
    
    private SubscriptionItem getPrimarySubscriptionItem(Subscription stripeSubscription) {
        if (stripeSubscription.getItems() == null
                || stripeSubscription.getItems().getData() == null
                || stripeSubscription.getItems().getData().isEmpty()) {
            throw new IllegalStateException("Stripe subscription did not include subscription items.");
        }

        return stripeSubscription.getItems().getData().get(0);
    }
}