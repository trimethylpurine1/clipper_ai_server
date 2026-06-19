package com.clipperai.product.security;

import com.clipperai.product.entity.AuditAction;
import com.clipperai.product.service.AuditService;
import com.clipperai.product.service.RequestInfoService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private final FirebaseAuth firebaseAuth;
    private final AuditService auditService;
    private final RequestInfoService requestInfoService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
    	
    	String ipAddress = requestInfoService.getClientIp(request);
    	String userAgent = requestInfoService.getUserAgent(request);

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || authHeader.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authHeader.startsWith("Bearer ")) {
            auditService.recordSystemAction(
                    AuditAction.AUTH_TOKEN_INVALID,
                    "AUTH",
                    null,
                    ipAddress,
                    userAgent,
                    Map.of(
                            "reason", "Authorization header did not start with Bearer",
                            "path", request.getRequestURI()
                    )
            );

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String idToken = authHeader.substring("Bearer ".length());

        try {
            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);

            FirebasePrincipal principal = new FirebasePrincipal(
                    decodedToken.getUid(),
                    decodedToken.getEmail(),
                    decodedToken.getName(),
                    decodedToken.isEmailVerified()
            );

            AbstractAuthenticationToken authentication =
                    new AbstractAuthenticationToken(AuthorityUtils.NO_AUTHORITIES) {
                        @Override
                        public Object getCredentials() {
                            return null;
                        }

                        @Override
                        public Object getPrincipal() {
                            return principal;
                        }
                    };

            authentication.setAuthenticated(true);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);

        } catch (FirebaseAuthException ex) {
            SecurityContextHolder.clearContext();

            auditService.recordSystemAction(
                    AuditAction.AUTH_TOKEN_INVALID,
                    "AUTH",
                    null,
                    ipAddress,
                    userAgent,
                    Map.of(
                            "reason", ex.getMessage(),
                            "path", request.getRequestURI()
                    )
            );

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}