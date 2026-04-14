package com.citybus.platform.application;

import com.citybus.platform.api.dto.AuthDtos;
import com.citybus.platform.domain.RecoveryCode;
import com.citybus.platform.domain.RoleName;
import com.citybus.platform.domain.SessionEntity;
import com.citybus.platform.domain.User;
import com.citybus.platform.infrastructure.persistence.RecoveryCodeRepository;
import com.citybus.platform.infrastructure.persistence.SessionRepository;
import com.citybus.platform.infrastructure.persistence.UserRepository;
import com.citybus.platform.infrastructure.security.AuthenticatedUser;
import com.citybus.platform.infrastructure.security.JwtTokenService;
import com.citybus.platform.infrastructure.security.RequestFingerprintService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RequestFingerprintService requestFingerprintService;
    private final HashingService hashingService;
    private final AuditService auditService;
    private final AppProperties properties;

    public AuthService(
            UserRepository userRepository,
            RecoveryCodeRepository recoveryCodeRepository,
            SessionRepository sessionRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            RequestFingerprintService requestFingerprintService,
            HashingService hashingService,
            AuditService auditService,
            AppProperties properties
    ) {
        this.userRepository = userRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.requestFingerprintService = requestFingerprintService;
        this.hashingService = hashingService;
        this.auditService = auditService;
        this.properties = properties;
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request, HttpServletRequest httpServletRequest) {
        User user = userRepository.findByUsername(request.username())
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditService.log(user.getId(), "LOGIN_FAILED", "USER", user.getId().toString(), "Invalid credentials");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        SessionEntity session = new SessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(user.getId());
        session.setRefreshTokenHash("");
        session.setUserAgent(requestFingerprintService.userAgent(httpServletRequest));
        session.setIpHint(requestFingerprintService.ipHint(httpServletRequest));
        session.setRevoked(false);
        session.setCreatedAt(Instant.now());
        session.setExpiresAt(Instant.now().plus(properties.security().refreshTokenDays(), ChronoUnit.DAYS));

        String refreshToken = jwtTokenService.createRefreshToken(user, session.getId());
        session.setRefreshTokenHash(hashingService.sha256(refreshToken));
        sessionRepository.save(session);
        auditService.log(user.getId(), "LOGIN_SUCCESS", "SESSION", session.getId().toString(), "User logged in");
        return new AuthDtos.AuthResponse(
                jwtTokenService.createAccessToken(user, session.getId()),
                refreshToken,
                toProfile(user),
                user.isTemporaryPassword()
        );
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserProfile me(AuthenticatedUser currentUser) {
        User user = userRepository.findById(currentUser.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
        return toProfile(user);
    }

    @Transactional
    public AuthDtos.AuthResponse refresh(AuthDtos.RefreshRequest request, HttpServletRequest httpServletRequest) {
        Claims claims;
        try {
            claims = jwtTokenService.parseRefreshToken(request.refreshToken());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid session");
        }
        UUID userId = UUID.fromString(claims.getSubject());
        UUID sessionId = UUID.fromString((String) claims.get("sessionId"));
        User user = userRepository.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid session"));
        SessionEntity session = sessionRepository.findById(sessionId)
                .filter(entity -> !entity.isRevoked())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid session"));

        if (!session.getRefreshTokenHash().equals(hashingService.sha256(request.refreshToken()))) {
            session.setRevoked(true);
            sessionRepository.save(session);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token replay detected");
        }
        if (!session.getUserAgent().equals(requestFingerprintService.userAgent(httpServletRequest))) {
            session.setRevoked(true);
            sessionRepository.save(session);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Session fingerprint changed");
        }

        String newRefreshToken = jwtTokenService.createRefreshToken(user, sessionId);
        session.setRefreshTokenHash(hashingService.sha256(newRefreshToken));
        session.setIpHint(requestFingerprintService.ipHint(httpServletRequest));
        session.setExpiresAt(Instant.now().plus(properties.security().refreshTokenDays(), ChronoUnit.DAYS));
        sessionRepository.save(session);

        return new AuthDtos.AuthResponse(
                jwtTokenService.createAccessToken(user, sessionId),
                newRefreshToken,
                toProfile(user),
                user.isTemporaryPassword()
        );
    }

    @Transactional
    public void logout(AuthDtos.LogoutRequest request) {
        Claims claims;
        try {
            claims = jwtTokenService.parseRefreshToken(request.refreshToken());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid session");
        }
        UUID sessionId = UUID.fromString((String) claims.get("sessionId"));
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setRevoked(true);
            sessionRepository.save(session);
            auditService.log(session.getUserId(), "LOGOUT", "SESSION", sessionId.toString(), "Session revoked");
        });
    }

    @Transactional
    public String recover(AuthDtos.RecoverRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        List<RecoveryCode> availableCodes = recoveryCodeRepository.findByUserIdAndUsedFalse(user.getId());
        for (RecoveryCode code : availableCodes) {
            if (passwordEncoder.matches(request.recoveryCodeOrRequestToken(), code.getCodeHash())) {
                code.setUsed(true);
                recoveryCodeRepository.save(code);
                String temporaryPassword = "Reset" + UUID.randomUUID().toString().substring(0, 8) + "!";
                user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
                user.setTemporaryPassword(true);
                user.setUpdatedAt(Instant.now());
                userRepository.save(user);
                revokeAllSessions(user.getId());
                auditService.log(user.getId(), "PASSWORD_RECOVERY", "USER", user.getId().toString(), "Recovery code used");
                return temporaryPassword;
            }
        }
        String requestToken = "REQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        auditService.log(user.getId(), "RECOVERY_REQUEST_TOKEN_ISSUED", "USER", user.getId().toString(), requestToken);
        return requestToken;
    }

    @Transactional
    public void adminResetPassword(UUID adminUserId, String username, String temporaryPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setTemporaryPassword(true);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        revokeAllSessions(user.getId());
        auditService.log(adminUserId, "ADMIN_PASSWORD_RESET", "USER", user.getId().toString(), "Temporary password issued");
    }

    @Transactional
    public void changePassword(AuthenticatedUser currentUser, AuthDtos.ChangePasswordRequest request) {
        User user = userRepository.findById(currentUser.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Current password is invalid");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "New password must differ from current password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setTemporaryPassword(false);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        revokeAllSessions(user.getId());
        auditService.log(user.getId(), "PASSWORD_CHANGED", "USER", user.getId().toString(), "Password updated");
    }

    @Transactional
    public void revokeAllSessions(UUID userId) {
        sessionRepository.findByUserIdAndRevokedFalse(userId).forEach(session -> {
            session.setRevoked(true);
            sessionRepository.save(session);
        });
    }

    private AuthDtos.UserProfile toProfile(User user) {
        return new AuthDtos.UserProfile(user.getId().toString(), user.getUsername(), user.getDisplayName(), user.getRoleName());
    }
}
