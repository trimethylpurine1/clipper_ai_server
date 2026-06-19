package com.clipperai.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.clipperai.product.entity.UsagePeriod;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsagePeriodRepository extends JpaRepository<UsagePeriod, UUID> {

    List<UsagePeriod> findByUserIdOrderByPeriodStartDesc(String userId);

    Optional<UsagePeriod> findByUserIdAndPeriodStartAndPeriodEnd(
            String userId,
            OffsetDateTime periodStart,
            OffsetDateTime periodEnd
    );

    @Query("""
            SELECT up
            FROM UsagePeriod up
            WHERE up.user.id = :userId
              AND up.periodStart <= :now
              AND up.periodEnd > :now
            ORDER BY up.periodStart DESC
            """)
    Optional<UsagePeriod> findCurrentByUserId(
            @Param("userId") String userId,
            @Param("now") OffsetDateTime now
    );

    @Query("""
            SELECT up
            FROM UsagePeriod up
            WHERE up.subscription.id = :subscriptionId
            ORDER BY up.periodStart DESC
            """)
    List<UsagePeriod> findBySubscriptionIdOrderByPeriodStartDesc(
            @Param("subscriptionId") UUID subscriptionId
    );
}