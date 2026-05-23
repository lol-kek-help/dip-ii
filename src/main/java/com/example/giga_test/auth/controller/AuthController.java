package com.example.giga_test.auth.controller;

import com.example.giga_test.auth.dto.AuthRequest;
import com.example.giga_test.auth.dto.AuthResponse;
import com.example.giga_test.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {this.authService = authService;}

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {return ResponseEntity.ok().build();}

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody @Valid AuthRequest request) {return ResponseEntity.ok(authService.login(request));}
}
