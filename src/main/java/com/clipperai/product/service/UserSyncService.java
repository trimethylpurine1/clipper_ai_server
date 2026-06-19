package com.clipperai.product.service;

import com.clipperai.product.entity.AppUser;
import com.clipperai.product.entity.AuditAction;
import com.clipperai.product.repository.AppUserRepository;
import com.clipperai.product.security.FirebasePrincipal;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserSyncService {

    private final AppUserRepository appUserRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final RequestInfoService requestInfoService;
    
    @Transactional
    public AppUser syncCurrentUser(HttpServletRequest request) {
    	String ipAddress = requestInfoService.getClientIp(request);
        String userAgent = requestInfoService.getUserAgent(request);
        FirebasePrincipal principal = currentUserService.getFirebasePrincipal();

        AppUser user = appUserRepository.findById(principal.uid()).orElse(null);

        boolean created = false;

        if (user == null) {
            user = AppUser.builder()
                    .id(principal.uid())
                    .email(principal.email())
                    .displayName(principal.name())
                    .createdAt(OffsetDateTime.now())
                    .emailVerified(principal.emailVerified())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            created = true;
        } else {
            user.setEmail(principal.email());
            user.setDisplayName(principal.name());
            user.setEmailVerified(principal.emailVerified());
            user.setUpdatedAt(OffsetDateTime.now());
        }

        AppUser savedUser = appUserRepository.save(user);

        auditService.recordUserAction(
                savedUser,
                created ? AuditAction.USER_CREATED : AuditAction.USER_SYNCED,
                "APP_USER",
                null,
                ipAddress,
                userAgent,
                Map.of(
                        "firebaseUid", principal.uid(),
                        "email", principal.email() == null ? "" : principal.email(),
                        "created", created
                )
        );

        auditService.recordUserAction(
                savedUser,
                AuditAction.USER_LOGIN,
                "APP_USER",
                null,
                ipAddress,
                userAgent,
                Map.of(
                        "firebaseUid", principal.uid(),
                        "email", principal.email() == null ? "" : principal.email()
                )
        );

        return savedUser;
    }
}