package com.clipperai.product.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clipperai.product.entity.BillingInvoice;

public interface BillingInvoiceRepository extends JpaRepository<BillingInvoice, UUID> {
    Optional<BillingInvoice> findByStripeInvoiceId(String stripeInvoiceId);
    Optional<BillingInvoice> findByStripePaymentIntentId(String stripePaymentIntentId);
    boolean existsByStripeInvoiceId(String stripeInvoiceId);
}