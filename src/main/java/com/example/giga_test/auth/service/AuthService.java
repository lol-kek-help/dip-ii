package com.example.giga_test.auth.service;

import com.example.giga_test.auth.dto.AuthRequest;
import com.example.giga_test.auth.dto.AuthResponse;
import com.example.giga_test.auth.repository.UserRepository;
import com.example.giga_test.security.JwtService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse login(AuthRequest request) {
        var user = userRepository.findByUsername(request.username()).orElseThrow(() -> new EntityNotFoundException("Пользователь не найден"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Неверный пароль");
        }
        String token = jwtService.generateToken(user.getUsername(), user.getRole().name());
        return new AuthResponse(token, "Bearer", jwtService.getExpirationSec());
    }
}
