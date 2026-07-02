package com.clipperai.product.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.clipperai.product.entity.*;
import com.clipperai.product.entity.billing.CreditGrant;
import com.clipperai.product.entity.billing.CreditGrantStatus;
import com.clipperai.product.entity.billing.CreditType;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditGrantRepository extends JpaRepository<CreditGrant, UUID> {

    Optional<CreditGrant> findByStripePaymentIntentId(String stripePaymentIntentId);

    List<CreditGrant> findByUserAndStatusAndCreditTypeOrderByCreatedAtAsc(
            AppUser user,
            CreditGrantStatus status,
            CreditType creditType
    );
    
    
}