package com.example.giga_test.auth.repository;

import com.example.giga_test.auth.entity.RefreshTokenEntity;
import com.example.giga_test.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {
    Optional<RefreshTokenEntity> findByToken(String token);
    void deleteByUser(User user);
}
