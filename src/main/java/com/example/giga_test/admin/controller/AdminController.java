package com.example.giga_test.admin.controller;

import com.example.giga_test.audit.entity.AuditLog;
import com.example.giga_test.audit.repository.AuditLogRepository;
import com.example.giga_test.auth.repository.UserRepository;
import com.example.giga_test.model.Category;
import com.example.giga_test.model.Priority;
import com.example.giga_test.model.Status;
import com.example.giga_test.model.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminController {
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    public AdminController(UserRepository userRepository, AuditLogRepository auditLogRepository) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/users")
    public List<User> users() {
        return userRepository.findAll();
    }

    @GetMapping("/audit")
    public List<AuditLog> audit(@RequestParam(name = "limit", defaultValue = "100") int limit) {
        return auditLogRepository.findAll().stream().sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt())).limit(Math.max(1, limit)).toList();
    }

    @GetMapping("/dictionaries")
    public Dictionaries dictionaries() {
        return new Dictionaries(
                Arrays.stream(Status.values()).map(Enum::name).toList(),
                Arrays.stream(Priority.values()).map(Enum::name).toList(),
                Arrays.stream(Category.values()).map(Enum::name).toList()
        );
    }

    public record Dictionaries(List<String> statuses, List<String> priorities, List<String> categories) {}
}
