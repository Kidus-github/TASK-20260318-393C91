package com.citybus.platform.api.dto;

import com.citybus.platform.domain.RoleName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank @Size(min = 8, max = 128) String password
    ) {}

    public record RecoverRequest(
            @NotBlank String username,
            @NotBlank String recoveryCodeOrRequestToken
    ) {}

    public record RefreshRequest(
            @NotBlank String refreshToken
    ) {}

    public record LogoutRequest(
            @NotBlank String refreshToken
    ) {}

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            UserProfile profile,
            boolean passwordChangeRequired
    ) {}

    public record UserProfile(
            String userId,
            String username,
            String displayName,
            RoleName role
    ) {}
}
