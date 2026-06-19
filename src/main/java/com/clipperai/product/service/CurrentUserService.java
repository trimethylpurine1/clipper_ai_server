package com.clipperai.product.service;

import com.clipperai.product.entity.AppUser;
import com.clipperai.product.repository.AppUserRepository;
import com.clipperai.product.security.FirebasePrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final AppUserRepository appUserRepository;

    public FirebasePrincipal getFirebasePrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof FirebasePrincipal principal)) {
            throw new IllegalStateException("No authenticated Firebase user found");
        }

        return principal;
    }

    public AppUser getCurrentUser() {
        FirebasePrincipal principal = getFirebasePrincipal();

        return appUserRepository.findById(principal.uid())
                .orElseThrow(() -> new IllegalStateException("Authenticated Firebase user has not been synced"));
    }
}