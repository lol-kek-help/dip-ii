package com.example.giga_test;

import com.example.giga_test.auth.dto.AuthRequest;
import com.example.giga_test.auth.dto.RefreshTokenRequest;
import com.example.giga_test.auth.entity.RefreshTokenEntity;
import com.example.giga_test.auth.repository.RefreshTokenRepository;
import com.example.giga_test.auth.repository.UserRepository;
import com.example.giga_test.auth.service.AuthService;
import com.example.giga_test.exceptions.AuthException;
import com.example.giga_test.model.RoleName;
import com.example.giga_test.model.User;
import com.example.giga_test.security.JwtService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    @Test
    void loginShouldIssueAccessAndRefreshTokens() {
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtService jwtService = mock(JwtService.class);
        AuthService service = new AuthService(userRepository, refreshTokenRepository, passwordEncoder, jwtService, 3600);
        User user = user(1L, "operator1", RoleName.OPERATOR);

        when(userRepository.findByUsername("operator1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);
        when(jwtService.generateToken("operator1", "OPERATOR")).thenReturn("access-token");
        when(jwtService.getExpirationSec()).thenReturn(900L);

        var response = service.login(new AuthRequest("operator1", "password"));

        assertEquals("access-token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(900L, response.expiresIn());
        assertNotNull(response.refreshToken());
        assertFalse(response.refreshToken().isBlank());
        verify(refreshTokenRepository).deleteByUser(user);
        ArgumentCaptor<RefreshTokenEntity> tokenCaptor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        RefreshTokenEntity savedToken = tokenCaptor.getValue();
        assertEquals(user, savedToken.getUser());
        assertNotEquals(response.refreshToken(), savedToken.getToken());
        assertFalse(savedToken.isRevoked());
        assertTrue(savedToken.getExpiresAt().isAfter(Instant.now()));
    }

    @Test
    void loginShouldRejectWrongPassword() {
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtService jwtService = mock(JwtService.class);
        AuthService service = new AuthService(userRepository, refreshTokenRepository, passwordEncoder, jwtService, 3600);
        User user = user(1L, "user1", RoleName.USER);

        when(userRepository.findByUsername("user1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThrows(AuthException.class, () -> service.login(new AuthRequest("user1", "wrong")));
    }

    @Test
    void refreshShouldRevokeOldTokenAndIssueNewPair() {
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtService jwtService = mock(JwtService.class);
        AuthService service = new AuthService(userRepository, refreshTokenRepository, passwordEncoder, jwtService, 3600);
        User user = user(2L, "admin1", RoleName.ADMIN);
        RefreshTokenEntity storedToken = new RefreshTokenEntity();
        storedToken.setUser(user);
        storedToken.setToken("hashed-old-token");
        storedToken.setExpiresAt(Instant.now().plusSeconds(60));
        storedToken.setRevoked(false);

        when(refreshTokenRepository.findByToken(anyString())).thenReturn(Optional.of(storedToken));
        when(jwtService.generateToken("admin1", "ADMIN")).thenReturn("new-access-token");
        when(jwtService.getExpirationSec()).thenReturn(900L);

        var response = service.refresh(new RefreshTokenRequest("raw-refresh-token"));

        assertEquals("new-access-token", response.accessToken());
        assertTrue(storedToken.isRevoked());
        verify(refreshTokenRepository).save(storedToken);
        verify(refreshTokenRepository, times(2)).save(any(RefreshTokenEntity.class));
    }

    private User user(Long id, String username, RoleName role) {
        return User.builder()
                .id(id)
                .username(username)
                .name(username)
                .passwordHash("hash")
                .role(role)
                .build();
    }
}
