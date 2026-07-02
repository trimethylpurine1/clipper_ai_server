package com.clipperai.product.service.billing;

import com.clipperai.product.entity.AppUser;
import com.clipperai.product.entity.AuditAction;
import com.clipperai.product.entity.billing.BillingCustomer;
import com.clipperai.product.repository.BillingCustomerRepository;
import com.clipperai.product.service.AuditService;
import com.clipperai.product.service.CurrentUserService;
import com.clipperai.product.service.RequestInfoService;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerUpdateParams;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BillingCustomerService {

    private final BillingCustomerRepository billingCustomerRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final RequestInfoService requestInfoService;

    /**
     * Use this from authenticated billing endpoints.
     * The user comes from the verified Firebase Bearer token,
     * not from a frontend-supplied userId.
     */
    @Transactional
    public BillingCustomer getOrCreateForCurrentUser(HttpServletRequest request) throws StripeException {
        AppUser currentUser = currentUserService.getCurrentUser();
        return getOrCreateForUser(currentUser, request);
    }

    /**
     * Main method used by subscription/payment services.
     */
    @Transactional
    public BillingCustomer getOrCreateForUser(AppUser user, HttpServletRequest request) throws StripeException {
        Optional<BillingCustomer> existingCustomer =
                billingCustomerRepository.findByUserId(user.getId());

        if (existingCustomer.isPresent()) {
            return syncLocalEmailIfNeeded(existingCustomer.get(), user);
        }

        return createBillingCustomer(user, request);
    }

    @Transactional(readOnly = true)
    public BillingCustomer getRequiredForCurrentUser() {
        AppUser currentUser = currentUserService.getCurrentUser();

        return billingCustomerRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new IllegalStateException("No billing customer exists for this user."));
    }

    @Transactional(readOnly = true)
    public BillingCustomer getRequiredByStripeCustomerId(String stripeCustomerId) {
        return billingCustomerRepository.findByStripeCustomerId(stripeCustomerId)
                .orElseThrow(() -> new IllegalStateException(
                        "No local billing customer found for Stripe customer: " + stripeCustomerId
                ));
    }

    private BillingCustomer createBillingCustomer(AppUser user, HttpServletRequest request) throws StripeException {
    	
    	String ipAddress = requestInfoService.getClientIp(request);
        String userAgent = requestInfoService.getUserAgent(request);
    	
        CustomerCreateParams.Builder paramsBuilder = CustomerCreateParams.builder()
                .setEmail(user.getEmail())
                .putMetadata("app_user_id", user.getId());

        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            paramsBuilder.setName(user.getDisplayName());
        }

        /*
         * This prevents accidental duplicate Stripe Customers if the request is retried.
         * The key should be stable for this exact "create billing customer for this app user" operation.
         */
        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey("create-billing-customer-" + user.getId())
                .build();

        Customer stripeCustomer = Customer.create(paramsBuilder.build(), requestOptions);

        BillingCustomer billingCustomer = BillingCustomer.builder()
                .user(user)
                .stripeCustomerId(stripeCustomer.getId())
                .email(user.getEmail())
                .build();

        try {
            BillingCustomer saved = billingCustomerRepository.saveAndFlush(billingCustomer);

            /*
             * Now that we know the local billing_customer_id, attach it back to Stripe metadata.
             * This is useful for debugging and webhook reconciliation.
             */
            try {
                updateStripeCustomerMetadata(saved);
            } catch (StripeException metadataUpdateFailed) {
                // Optional metadata update failed.
                // Do not fail billing customer creation.
                // Later: log this with Logger or audit metadata.
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("stripeCustomerId", saved.getStripeCustomerId());
            metadata.put("email", saved.getEmail());

            auditService.recordUserAction(
                    user,
                    AuditAction.BILLING_CUSTOMER_CREATED,
                    "BILLING_CUSTOMER",
                    saved.getId(),
                    ipAddress,
                    userAgent,
                    metadata
            );

            return saved;

        } catch (DataIntegrityViolationException duplicateRace) {
            /*
             * If two requests race each other, the unique user_id constraint protects the DB.
             * Fetch the row that won instead of creating another local record.
             */
            return billingCustomerRepository.findByUserId(user.getId())
                    .orElseThrow(() -> duplicateRace);
        }
    }

    private BillingCustomer syncLocalEmailIfNeeded(BillingCustomer billingCustomer, AppUser user) {
        if (user.getEmail() == null) {
            return billingCustomer;
        }

        if (!user.getEmail().equals(billingCustomer.getEmail())) {
            billingCustomer.setEmail(user.getEmail());
            return billingCustomerRepository.save(billingCustomer);
        }

        return billingCustomer;
    }

    private void updateStripeCustomerMetadata(BillingCustomer billingCustomer) throws StripeException {
        Customer customer = Customer.retrieve(billingCustomer.getStripeCustomerId());

        CustomerUpdateParams params = CustomerUpdateParams.builder()
                .setEmail(billingCustomer.getEmail())
                .putMetadata("app_user_id", billingCustomer.getUser().getId())
                .putMetadata("billing_customer_id", billingCustomer.getId().toString())
                .build();

        customer.update(params);
    }
}