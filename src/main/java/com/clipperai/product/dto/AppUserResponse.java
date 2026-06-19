package com.clipperai.product.dto;

import com.clipperai.product.entity.AppUser;

import java.time.OffsetDateTime;

public record AppUserResponse(
        String id,
        String email,
        String displayName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AppUserResponse fromEntity(AppUser user) {
        return new AppUserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}