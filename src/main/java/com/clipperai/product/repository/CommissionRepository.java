package com.clipperai.product.repository;

import com.clipperai.product.entity.Commission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommissionRepository extends JpaRepository<Commission, UUID> {

    Optional<Commission> findByStripeInvoiceId(String stripeInvoiceId);

    boolean existsByStripeInvoiceId(String stripeInvoiceId);

    Optional<Commission> findByBillingInvoiceId(UUID billingInvoiceId);

    boolean existsByBillingInvoiceId(UUID billingInvoiceId);

    List<Commission> findByAffiliateIdOrderByCreatedAtDesc(UUID affiliateId);

    List<Commission> findByAffiliateIdAndStatusOrderByCreatedAtDesc(
            UUID affiliateId,
            String status
    );

    List<Commission> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Commission> findByStatusAndAvailableAfterLessThanEqualOrderByAvailableAfterAsc(
            String status,
            OffsetDateTime now
    );
}