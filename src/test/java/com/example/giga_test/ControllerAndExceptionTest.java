package com.example.giga_test;

import com.example.giga_test.admin.controller.AdminController;
import com.example.giga_test.audit.entity.AuditLog;
import com.example.giga_test.audit.repository.AuditLogRepository;
import com.example.giga_test.auth.controller.AuthController;
import com.example.giga_test.auth.dto.AuthRequest;
import com.example.giga_test.auth.dto.AuthTokenResponse;
import com.example.giga_test.auth.dto.LogoutRequest;
import com.example.giga_test.auth.dto.RefreshTokenRequest;
import com.example.giga_test.auth.repository.UserRepository;
import com.example.giga_test.auth.service.AuthService;
import com.example.giga_test.exceptions.AuthException;
import com.example.giga_test.exceptions.GlobalExceptionHandler;
import com.example.giga_test.model.Category;
import com.example.giga_test.model.Priority;
import com.example.giga_test.model.RoleName;
import com.example.giga_test.model.Status;
import com.example.giga_test.model.User;
import com.example.giga_test.sla.controller.SlaController;
import com.example.giga_test.sla.dto.SlaReportDto;
import com.example.giga_test.sla.service.SlaService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ControllerAndExceptionTest {

    @Test
    void adminControllerShouldReturnUsersDictionariesAndAuditPage() {
        UserRepository userRepository = mock(UserRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        AdminController controller = new AdminController(userRepository, auditLogRepository);
        User admin = User.builder().id(1L).username("admin1").name("Admin").role(RoleName.ADMIN).build();
        AuditLog audit = new AuditLog();
        audit.setId(10L);
        audit.setAction("CREATE");
        audit.setEntityType("TICKET");
        audit.setCreatedAt(LocalDateTime.now());

        when(userRepository.findAll()).thenReturn(List.of(admin));
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(audit)));

        assertEquals("admin1", controller.users().get(0).username());
        var page = controller.audit(-10, 500, "CREATE", "TICKET", 1L, null, null);
        assertEquals(0, page.pageNumber());
        assertEquals(100, page.pageSize());
        assertEquals(1, page.items().size());
        var dictionaries = controller.dictionaries();
        assertTrue(dictionaries.statuses().contains(Status.NEW.name()));
        assertTrue(dictionaries.priorities().contains(Priority.URGENT.name()));
        assertTrue(dictionaries.categories().contains(Category.GENERAL.name()));
    }

    @Test
    void authControllerShouldDelegateToService() {
        AuthService service = mock(AuthService.class);
        AuthController controller = new AuthController(service);
        AuthTokenResponse response = new AuthTokenResponse("access", "refresh", "Bearer", 3600);

        when(service.login(new AuthRequest("user1", "password"))).thenReturn(response);
        when(service.refresh(new RefreshTokenRequest("refresh"))).thenReturn(response);

        assertEquals(response, controller.login(new AuthRequest("user1", "password")).getBody());
        assertEquals(response, controller.refresh(new RefreshTokenRequest("refresh")).getBody());
        assertEquals(HttpStatus.NO_CONTENT, controller.logout(new LogoutRequest("refresh")).getStatusCode());
        verify(service).logout(new LogoutRequest("refresh"));
    }

    @Test
    void slaControllerShouldDelegateDateRangeToService() {
        SlaService service = mock(SlaService.class);
        SlaController controller = new SlaController(service);
        SlaReportDto report = emptyReport();
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        when(service.report(from, to)).thenReturn(report);

        assertEquals(report, controller.report(from, to).getBody());
    }

    @Test
    void globalExceptionHandlerShouldMapCommonExceptions() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        assertEquals(HttpStatus.NOT_FOUND, handler.handleEntityNotFound(new EntityNotFoundException("missing")).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, handler.handleAuthException(new AuthException("bad auth")).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, handler.handleAccessDenied(new AccessDeniedException("denied")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, handler.handleBadRequest(new IllegalArgumentException("bad request")).getStatusCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, handler.handleGeneralException(new RuntimeException("boom")).getStatusCode());
    }

    private SlaReportDto emptyReport() {
        return new SlaReportDto(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
