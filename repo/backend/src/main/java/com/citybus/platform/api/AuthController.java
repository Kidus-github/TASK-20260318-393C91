package com.citybus.platform.api;

import com.citybus.platform.api.dto.AuthDtos;
import com.citybus.platform.application.AuthService;
import com.citybus.platform.infrastructure.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request, HttpServletRequest httpServletRequest) {
        return authService.login(request, httpServletRequest);
    }

    @PostMapping("/refresh")
    public AuthDtos.AuthResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request, HttpServletRequest httpServletRequest) {
        return authService.refresh(request, httpServletRequest);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@Valid @RequestBody AuthDtos.LogoutRequest request) {
        authService.logout(request);
        return Map.of("status", "ok");
    }

    @PostMapping("/recover")
    public Map<String, String> recover(@Valid @RequestBody AuthDtos.RecoverRequest request) {
        return Map.of("result", authService.recover(request));
    }

    @GetMapping("/me")
    public AuthDtos.UserProfile me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return authService.me(currentUser);
    }
}
