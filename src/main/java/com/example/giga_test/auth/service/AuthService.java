package com.example.giga_test.auth.service;

import com.example.giga_test.auth.dto.*;
import com.example.giga_test.auth.entity.RefreshTokenEntity;
import com.example.giga_test.auth.repository.RefreshTokenRepository;
import com.example.giga_test.auth.repository.UserRepository;
import com.example.giga_test.exceptions.AuthException;
import com.example.giga_test.model.User;
import com.example.giga_test.security.JwtService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long refreshExpirationSec;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, @Value("${security.jwt.refresh-expiration-seconds:2592000}") long refreshExpirationSec) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshExpirationSec = refreshExpirationSec;
    }

    @Transactional
    public AuthTokenResponse login(AuthRequest request) {
        var user = userRepository.findByUsername(request.username()).orElseThrow(() ->
                new EntityNotFoundException("Пользователь не найден"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthException("Неверный пароль");
        }
        refreshTokenRepository.deleteByUser(user); //удаляет старые токены
        return issueTokens(user);
    }

    @Transactional
    public AuthTokenResponse refresh(RefreshTokenRequest request) {
        RefreshTokenEntity tokenEntity = refreshTokenRepository.findByToken(hashRefreshToken(request.refreshToken()))
                .orElseThrow(() -> new AuthException("Refresh token не найден"));
        if (tokenEntity.isRevoked()) {
            throw new AuthException("Refresh token отозван");
        }
        if (tokenEntity.getExpiresAt().isBefore(Instant.now())) {
            tokenEntity.setRevoked(true);
            refreshTokenRepository.save(tokenEntity);
            throw new AuthException("Refresh token истек");
        }
        User user = tokenEntity.getUser();
        tokenEntity.setRevoked(true);
        refreshTokenRepository.save(tokenEntity);
        return issueTokens(user);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        RefreshTokenEntity tokenEntity = refreshTokenRepository.findByToken(hashRefreshToken(request.refreshToken()))
                .orElseThrow(() -> new AuthException("Refresh token не найден"));
        tokenEntity.setRevoked(true);
        refreshTokenRepository.save(tokenEntity);
    }

    private AuthTokenResponse issueTokens(User user) {
        String accessToken = jwtService.generateToken(user.getUsername(), user.getRole().name());
        String refreshToken = generateRefreshToken();

        RefreshTokenEntity refreshTokenEntity = new RefreshTokenEntity();
        refreshTokenEntity.setUser(user);
        refreshTokenEntity.setToken(hashRefreshToken(refreshToken));
        refreshTokenEntity.setExpiresAt(Instant.now().plusSeconds(refreshExpirationSec));
        refreshTokenEntity.setRevoked(false);
        refreshTokenRepository.save(refreshTokenEntity);

        return new AuthTokenResponse(accessToken, refreshToken, "Bearer", jwtService.getExpirationSec());
    }

    private String generateRefreshToken() {
        byte[] randomBytes = new byte[48];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hashRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthException("Refresh token не передан");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }
}
