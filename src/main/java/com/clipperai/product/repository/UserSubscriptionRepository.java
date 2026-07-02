package com.clipperai.product.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clipperai.product.entity.AppUser;
import com.clipperai.product.entity.billing.UserSubscription;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, UUID> {
    Optional<UserSubscription> findByStripeSubscriptionId(String stripeSubscriptionId);
    Optional<UserSubscription> findTopByUserIdOrderByCreatedAtDesc(String userId);
    
    Optional<UserSubscription> findTopByUserOrderByCreatedAtDesc(AppUser user);

    Optional<UserSubscription> findTopByUserAndStatusInOrderByCreatedAtDesc(
            AppUser user,
            Collection<String> statuses
    );
}