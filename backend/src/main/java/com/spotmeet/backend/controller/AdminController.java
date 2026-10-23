package com.spotmeet.backend.controller;

import com.spotmeet.backend.dto.*;
import com.spotmeet.backend.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Administrative routes protected by JWT and ADMIN / SYSADMIN roles. */
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
@PreAuthorize("hasAnyRole('ADMIN', 'SYSADMIN')")
public class AdminController {

    @Autowired
    private AdminService adminService;

    /**
     * GET /api/admin/organizations
     * Lists all registered organizations with LGPD masking.
     */
    @GetMapping("/organizations")
    public ResponseEntity<?> listOrganizations(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        try {
            List<AdminOrganizationDTO> list = adminService.listOrganizations(authentication.getName());
            return ResponseEntity.ok(list);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao listar organizações: " + e.getMessage());
        }
    }

    /**
     * GET /api/admin/organizations/pending
     * Lists organizations pending SysAdmin approval.
     */
    @GetMapping("/organizations/pending")
    public ResponseEntity<?> listPendingOrganizations(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        try {
            List<AdminOrganizationDTO> pending = adminService.listPendingOrganizations(authentication.getName());
            return ResponseEntity.ok(pending);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao listar pendências: " + e.getMessage());
        }
    }

    /**
     * POST /api/admin/organizations/{id}/approve
     * Approves a pending organization.
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<?> invalidModerationBody() {
        return ResponseEntity.badRequest().body(Map.of("message", "Dados da moderação inválidos."));
    }

    @PostMapping("/organizations/{id}/approve")
    public ResponseEntity<?> approveOrganization(
            @PathVariable Long id,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        try {
            AdminOrganizationDTO response = adminService.approveOrganization(id, authentication.getName());
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao aprovar organização: " + e.getMessage());
        }
    }

    /**
     * POST /api/admin/organizations/{id}/reject
     * Rejects an organization with a mandatory moderation reason.
     */
    @PostMapping("/organizations/{id}/reject")
    public ResponseEntity<?> rejectOrganization(
            @PathVariable Long id,
            @Valid @RequestBody com.spotmeet.backend.dto.OrganizationRejectionDTO body,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        try {
            String reason = body.getReason();
            AdminOrganizationDTO response = adminService.rejectOrganization(id, reason, authentication.getName());
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (com.spotmeet.backend.exception.InvalidModerationReasonException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao rejeitar organização: " + e.getMessage());
        }
    }

    /**
     * PATCH /api/admin/organizations/{id}/status
     * Approves or blocks an organization.
     */
    @PatchMapping("/organizations/{id}/status")
    public ResponseEntity<?> updateOrganizationStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrganizationStatusDTO dto,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        try {
            AdminOrganizationDTO response = adminService.updateOrganizationStatus(
                    id, dto.getApproved(), dto.getJustification(), authentication.getName()
            );
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (com.spotmeet.backend.exception.InvalidModerationReasonException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao atualizar status: " + e.getMessage());
        }
    }

    /** Returns the current database, network and JVM status. */
    @GetMapping("/system/status")
    public ResponseEntity<?> getSystemStatus(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        try {
            SystemStatusDTO status = adminService.getSystemStatus(authentication.getName());
            return ResponseEntity.ok(status);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Não foi possível consultar o status do sistema.");
        }
    }

    @GetMapping("/system/details")
    public ResponseEntity<?> getSystemDetails(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        try {
            return ResponseEntity.ok(adminService.getSystemDetails(authentication.getName()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Não foi possível consultar os detalhes do sistema.");
        }
    }

    /**
     * GET /api/admin/reports
     * Returns aggregated metrics with masked sensitive data (LGPD).
     */
    @GetMapping("/reports")
    public ResponseEntity<?> getReports(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        try {
            AdminReportDTO report = adminService.getReports(authentication.getName());
            return ResponseEntity.ok(report);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao gerar relatórios: " + e.getMessage());
        }
    }

    /**
     * POST /api/admin/system/restart
     * Audited critical action for a safe restart of subsystems.
     */
    @PostMapping("/system/restart")
    public ResponseEntity<?> restartSystem(
            @RequestBody(required = false) CriticalActionRequestDTO dto,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        try {
            Map<String, Object> result = adminService.restartSystem(dto, authentication.getName());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao reiniciar subsistemas: " + e.getMessage());
        }
    }

    /**
     * POST /api/admin/system/reset
     * High-risk critical action: full system reset with strict confirmation.
     */
    @PostMapping("/system/reset")
    public ResponseEntity<?> resetSystem(
            @Valid @RequestBody CriticalActionRequestDTO dto,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        try {
            Map<String, Object> result = adminService.resetSystem(dto, authentication.getName());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao executar reset: " + e.getMessage());
        }
    }
}
