package com.devmind.module.auth.controller;

import com.devmind.common.api.Result;
import com.devmind.common.security.AuthenticatedUser;
import com.devmind.module.auth.dto.LoginRequest;
import com.devmind.module.auth.dto.RegisterRequest;
import com.devmind.module.auth.service.AuthService;
import com.devmind.module.auth.vo.LoginResponse;
import com.devmind.module.auth.vo.UserProfileResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<UserProfileResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @GetMapping("/me")
    public Result<UserProfileResponse> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(authService.getCurrentUser(user.userId()));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success();
    }
}
