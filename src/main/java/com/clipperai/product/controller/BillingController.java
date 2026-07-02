package com.clipperai.product.controller;

import com.clipperai.product.dto.billing.BillingCustomerResponse;
import com.clipperai.product.dto.billing.BillingPlanResponse;
import com.clipperai.product.dto.billing.StartSubscriptionRequest;
import com.clipperai.product.dto.billing.StartSubscriptionResponse;
import com.clipperai.product.entity.billing.BillingCustomer;
import com.clipperai.product.service.billing.BillingCustomerService;
import com.clipperai.product.service.billing.BillingPlanService;
import com.clipperai.product.service.billing.SubscriptionBillingService;
import com.clipperai.product.dto.billing.StartExtraCreditPurchaseRequest;
import com.clipperai.product.dto.billing.StartExtraCreditPurchaseResponse;
import com.clipperai.product.service.billing.ExtraVideoCreditPurchaseService;
import com.clipperai.product.dto.billing.CancelSubscriptionResponse;


import com.stripe.exception.StripeException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingCustomerService billingCustomerService;
    private final BillingPlanService billingPlanService;
    private final SubscriptionBillingService subscriptionBillingService;
    private final ExtraVideoCreditPurchaseService extraVideoCreditPurchaseService;
    
    //This is just for testing

    @PostMapping("/customers/me")
    public BillingCustomerResponse getOrCreateBillingCustomer(
            HttpServletRequest request
    ) throws StripeException {
        BillingCustomer billingCustomer =
                billingCustomerService.getOrCreateForCurrentUser(request);

        return BillingCustomerResponse.from(billingCustomer);
    }

    @GetMapping("/customers/me")
    public BillingCustomerResponse getExistingBillingCustomer() {
        BillingCustomer billingCustomer =
                billingCustomerService.getRequiredForCurrentUser();

        return BillingCustomerResponse.from(billingCustomer);
    }
    
    
    // Below is real end points
    
    @GetMapping("/plans")
    public List<BillingPlanResponse> getPlans() {
        return billingPlanService.getActivePlans();}
    

    @PostMapping("/subscriptions/start")
    public StartSubscriptionResponse startSubscription(
            @Valid @RequestBody StartSubscriptionRequest requestDto,
            HttpServletRequest request
    ) throws StripeException {
        return subscriptionBillingService.startSubscription(requestDto, request);
    }
    
    @PostMapping("/extra-video-credits/start")
    public StartExtraCreditPurchaseResponse startExtraVideoCreditPurchase(
            @Valid @RequestBody StartExtraCreditPurchaseRequest requestDto,
            HttpServletRequest request
    ) throws StripeException {
        return extraVideoCreditPurchaseService.startPurchase(requestDto, request);
    }
    
    @PostMapping("/subscriptions/me/cancel")
    public CancelSubscriptionResponse cancelCurrentSubscription(
            HttpServletRequest request
    ) throws StripeException {
        return subscriptionBillingService.cancelCurrentSubscriptionAtPeriodEnd(request);
    }
}
    
