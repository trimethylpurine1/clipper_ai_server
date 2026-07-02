package com.clipperai.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clipperai.product.entity.billing.OneTimeCreditProduct;

import java.util.Optional;
import java.util.UUID;

public interface OneTimeCreditProductRepository extends JpaRepository<OneTimeCreditProduct, UUID> {

    Optional<OneTimeCreditProduct> findByCodeAndActiveTrue(String code);

    Optional<OneTimeCreditProduct> findByStripePriceId(String stripePriceId);
}