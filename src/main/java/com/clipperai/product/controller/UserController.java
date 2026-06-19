package com.clipperai.product.controller;


import com.clipperai.product.dto.AppUserResponse;
import com.clipperai.product.entity.AppUser;
import com.clipperai.product.service.CurrentUserService;
import com.clipperai.product.service.UserSyncService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserSyncService userSyncService;
    private final CurrentUserService currentUserService;

    @PostMapping("/sync")
    public AppUserResponse syncCurrentUser(HttpServletRequest request) {
        AppUser user = userSyncService.syncCurrentUser(request);
        return AppUserResponse.fromEntity(user);
    }

    @GetMapping("/me")
    public AppUserResponse getMe() {
        AppUser user = currentUserService.getCurrentUser();
        return AppUserResponse.fromEntity(user);
    }
}