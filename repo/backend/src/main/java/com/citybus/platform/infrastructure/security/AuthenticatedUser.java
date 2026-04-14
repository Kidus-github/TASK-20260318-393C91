package com.citybus.platform.infrastructure.security;

import com.citybus.platform.domain.RoleName;

import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        String username,
        RoleName roleName,
        UUID sessionId
) {
}
