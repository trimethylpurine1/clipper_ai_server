package com.clipperai.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clipperai.product.entity.BillingCustomer;

import java.util.Optional;
import java.util.UUID;

public interface BillingCustomerRepository extends JpaRepository<BillingCustomer, UUID>{
	
    Optional<BillingCustomer> findByUserId(String userId);
    Optional<BillingCustomer> findByStripeCustomerId(String stripeCustomerId);
    boolean existsByStripeCustomerId(String stripeCustomerId);

}
