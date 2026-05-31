package com.example.giga_test.user.controller;

import com.example.giga_test.auth.repository.UserRepository;
import com.example.giga_test.model.User;
import com.example.giga_test.user.dto.UserSummaryDto;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public UserSummaryDto me(Authentication authentication) {
        return UserSummaryDto.from(currentUser(authentication));
    }

    @GetMapping
    public List<UserSummaryDto> users(Authentication authentication) {
        currentUser(authentication);
        return userRepository.findAll().stream().map(UserSummaryDto::from).toList();
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new EntityNotFoundException("Текущий пользователь не найден");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new EntityNotFoundException("Текущий пользователь не найден"));
    }
}
