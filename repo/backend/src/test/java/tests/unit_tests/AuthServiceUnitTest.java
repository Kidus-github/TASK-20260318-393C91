package tests.unit_tests;

import com.citybus.platform.api.dto.AuthDtos;
import com.citybus.platform.application.ApiException;
import com.citybus.platform.application.AppProperties;
import com.citybus.platform.application.AuditService;
import com.citybus.platform.application.AuthService;
import com.citybus.platform.application.HashingService;
import com.citybus.platform.domain.RoleName;
import com.citybus.platform.domain.SessionEntity;
import com.citybus.platform.domain.User;
import com.citybus.platform.infrastructure.persistence.RecoveryCodeRepository;
import com.citybus.platform.infrastructure.persistence.SessionRepository;
import com.citybus.platform.infrastructure.persistence.UserRepository;
import com.citybus.platform.infrastructure.security.JwtTokenService;
import com.citybus.platform.infrastructure.security.RequestFingerprintService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceUnitTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private RecoveryCodeRepository recoveryCodeRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private RequestFingerprintService requestFingerprintService;
    @Mock
    private HashingService hashingService;
    @Mock
    private AuditService auditService;
    @Mock
    private HttpServletRequest request;

    private AuthService authService;
    private User activePassenger;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                "test",
                new AppProperties.Security(15, 7, "01234567890123456789012345678901", "01234567890123456789012345678902"),
                new AppProperties.Reminders(10, 15),
                new AppProperties.Queue(3),
                new AppProperties.Search(10, 7, 5, 3, 4),
                new AppProperties.Alerts(25, 500)
        );
        authService = new AuthService(
                userRepository,
                recoveryCodeRepository,
                sessionRepository,
                passwordEncoder,
                jwtTokenService,
                requestFingerprintService,
                hashingService,
                auditService,
                properties
        );
        activePassenger = new User();
        activePassenger.setId(UUID.randomUUID());
        activePassenger.setUsername("passenger");
        activePassenger.setDisplayName("Passenger One");
        activePassenger.setRoleName(RoleName.PASSENGER);
        activePassenger.setPasswordHash("encoded");
        activePassenger.setActive(true);
        activePassenger.setTemporaryPassword(false);
        activePassenger.setCreatedAt(Instant.now());
        activePassenger.setUpdatedAt(Instant.now());
    }

    @Test
    void loginReturnsTokensAndPersistsSession() {
        when(userRepository.findByUsername("passenger")).thenReturn(Optional.of(activePassenger));
        when(passwordEncoder.matches("Passenger123!", "encoded")).thenReturn(true);
        when(requestFingerprintService.userAgent(request)).thenReturn("agent");
        when(requestFingerprintService.ipHint(request)).thenReturn("127.0.0.1");
        when(jwtTokenService.createRefreshToken(eq(activePassenger), any(UUID.class))).thenReturn("refresh-token");
        when(jwtTokenService.createAccessToken(eq(activePassenger), any(UUID.class))).thenReturn("access-token");
        when(hashingService.sha256("refresh-token")).thenReturn("refresh-hash");

        AuthDtos.AuthResponse response = authService.login(new AuthDtos.LoginRequest("passenger", "Passenger123!"), request);

        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        assertEquals("passenger", response.profile().username());
        assertEquals(RoleName.PASSENGER, response.profile().role());
        ArgumentCaptor<SessionEntity> sessionCaptor = ArgumentCaptor.forClass(SessionEntity.class);
        verify(sessionRepository).save(sessionCaptor.capture());
        SessionEntity saved = sessionCaptor.getValue();
        assertNotNull(saved.getId());
        assertEquals(activePassenger.getId(), saved.getUserId());
        assertEquals("refresh-hash", saved.getRefreshTokenHash());
    }

    @Test
    void loginRejectsInvalidCredentials() {
        when(userRepository.findByUsername("passenger")).thenReturn(Optional.of(activePassenger));
        when(passwordEncoder.matches("wrong-pass", "encoded")).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class,
                () -> authService.login(new AuthDtos.LoginRequest("passenger", "wrong-pass"), request));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("Invalid credentials", exception.getMessage());
    }

    @Test
    void refreshRejectsReplayTokenWhenHashDiffers() {
        UUID sessionId = UUID.randomUUID();
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn(activePassenger.getId().toString());
        when(claims.get("sessionId")).thenReturn(sessionId.toString());
        when(jwtTokenService.parseRefreshToken("incoming-refresh")).thenReturn(claims);
        when(userRepository.findById(activePassenger.getId())).thenReturn(Optional.of(activePassenger));

        SessionEntity session = new SessionEntity();
        session.setId(sessionId);
        session.setUserId(activePassenger.getId());
        session.setRevoked(false);
        session.setRefreshTokenHash("stored-hash");
        session.setUserAgent("agent");
        session.setExpiresAt(Instant.now().plusSeconds(300));
        session.setCreatedAt(Instant.now());
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(hashingService.sha256("incoming-refresh")).thenReturn("incoming-hash");

        ApiException exception = assertThrows(ApiException.class,
                () -> authService.refresh(new AuthDtos.RefreshRequest("incoming-refresh"), request));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("Refresh token replay detected", exception.getMessage());
    }
}

