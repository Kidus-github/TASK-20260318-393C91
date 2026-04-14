package com.citybus.platform.infrastructure.security;

import com.citybus.platform.application.AppProperties;
import com.citybus.platform.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtTokenService {
    private final AppProperties properties;

    public JwtTokenService(AppProperties properties) {
        this.properties = properties;
    }

    public String createAccessToken(User user, UUID sessionId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("role", user.getRoleName().name())
                .claim("sessionId", sessionId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.security().accessTokenMinutes(), ChronoUnit.MINUTES)))
                .signWith(accessKey())
                .compact();
    }

    public String createRefreshToken(User user, UUID sessionId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .claim("sessionId", sessionId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.security().refreshTokenDays(), ChronoUnit.DAYS)))
                .signWith(refreshKey())
                .compact();
    }

    public Claims parseAccessToken(String token) {
        return Jwts.parser().verifyWith(accessKey()).build().parseSignedClaims(token).getPayload();
    }

    public Claims parseRefreshToken(String token) {
        return Jwts.parser().verifyWith(refreshKey()).build().parseSignedClaims(token).getPayload();
    }

    private SecretKey accessKey() {
        return Keys.hmacShaKeyFor(properties.security().accessSecret().getBytes(StandardCharsets.UTF_8));
    }

    private SecretKey refreshKey() {
        return Keys.hmacShaKeyFor(properties.security().refreshSecret().getBytes(StandardCharsets.UTF_8));
    }
}
