package com.example.giga_test.admin.controller;

import com.example.giga_test.audit.entity.AuditLog;
import com.example.giga_test.audit.repository.AuditLogRepository;
import com.example.giga_test.auth.repository.UserRepository;
import com.example.giga_test.common.dto.PageResponse;
import com.example.giga_test.model.Category;
import com.example.giga_test.model.Priority;
import com.example.giga_test.model.Status;
import com.example.giga_test.user.dto.UserSummaryDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
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
    public List<UserSummaryDto> users() {
        return userRepository.findAll().stream().map(UserSummaryDto::from).toList();
    }

    @GetMapping("/audit")
    public PageResponse<AuditLog> audit(@RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
                                        @RequestParam(name = "pageSize", defaultValue = "50") int pageSize,
                                        @RequestParam(name = "action", required = false) String action,
                                        @RequestParam(name = "entityType", required = false) String entityType,
                                        @RequestParam(name = "actorId", required = false) Long actorId,
                                        @RequestParam(name = "createdFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
                                        @RequestParam(name = "createdTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo) {
        int safePage = Math.max(0, pageNumber);
        int safeSize = Math.min(Math.max(1, pageSize), 100);
        Specification<AuditLog> spec = (root, query, cb) -> cb.conjunction();
        if (action != null && !action.isBlank()) spec = spec.and((r, q, cb) -> cb.equal(r.get("action"), action));
        if (entityType != null && !entityType.isBlank()) spec = spec.and((r, q, cb) -> cb.equal(r.get("entityType"), entityType));
        if (actorId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("actor").get("id"), actorId));
        if (createdFrom != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("createdAt"), createdFrom));
        if (createdTo != null) spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("createdAt"), createdTo));
        var page = auditLogRepository.findAll(spec, PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResponse<>(page.getContent(), safePage, safeSize, page.getTotalElements(), page.getTotalPages());
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
