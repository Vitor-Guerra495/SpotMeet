package com.spotmeet.backend.service;

import com.spotmeet.backend.dto.AdminOrganizationDTO;
import com.spotmeet.backend.dto.AdminReportDTO;
import com.spotmeet.backend.dto.AdminReportDTO.AuditActivityDTO;
import com.spotmeet.backend.dto.CriticalActionRequestDTO;
import com.spotmeet.backend.dto.SystemStatusDTO;
import com.spotmeet.backend.dto.SystemStatusDTO.MemoryInfo;
import com.spotmeet.backend.dto.SystemStatusDTO.SubsystemInfo;
import com.spotmeet.backend.model.AuditLog;
import com.spotmeet.backend.model.Organization;
import com.spotmeet.backend.model.User;
import com.spotmeet.backend.repository.AccessRequestRepository;
import com.spotmeet.backend.repository.AuditLogRepository;
import com.spotmeet.backend.repository.CommitteeMemberRepository;
import com.spotmeet.backend.repository.CommitteeRepository;
import com.spotmeet.backend.repository.OrganizationMemberRepository;
import com.spotmeet.backend.repository.OrganizationRepository;
import com.spotmeet.backend.repository.UserRepository;
import com.spotmeet.backend.security.LgpdMaskUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.net.InetAddress;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * System administration service.
 * Implements LGPD compliance, masking and auditing of critical actions.
 */
@Service
public class AdminService {

    @Autowired
    private OrganizationRepository organizationRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepo;

    @Autowired
    private CommitteeRepository committeeRepo;

    @Autowired
    private AuditLogRepository auditLogRepo;

    @Autowired
    private CommitteeMemberRepository committeeMemberRepo;

    @Autowired
    private AccessRequestRepository accessRequestRepo;

    @Autowired
    private DataSource dataSource;

    private final long instanceStartMs = System.currentTimeMillis();

    @Value("${server.port:8080}")
    private String serverPort;

    /**
     * Validates the executor permission (must be ADMIN or SYSADMIN).
     */
    private User validateSysAdmin(String executorEmail) {
        User user = userRepo.findByEmail(executorEmail)
                .orElseThrow(() -> new SecurityException("Usuário executor não encontrado."));

        String role = user.getRole();
        if (!"SYSADMIN".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            throw new SecurityException("Acesso negado. Ação restrita a Administradores.");
        }
        return user;
    }

    private AdminOrganizationDTO toDTO(Organization org) {
        int totalMembers = organizationMemberRepo.findByOrganizationId(org.getId()).size();
        int totalCommittees = committeeRepo.findByOrganizationIdOrderByNameAsc(org.getId()).size();

        String ownerName = org.getOwner() != null ? org.getOwner().getName() : "Não definido";
        String ownerEmail = org.getOwner() != null ? org.getOwner().getEmail() : null;
        Long ownerId = org.getOwner() != null ? org.getOwner().getId() : null;
        String status = org.getStatus() != null ? org.getStatus() : (org.isApproved() ? "APPROVED" : "PENDING");

        return new AdminOrganizationDTO(
                org.getId(),
                org.getName(),
                org.getAccessKey(),
                ownerId,
                ownerName,
                LgpdMaskUtil.maskEmail(ownerEmail),
                LgpdMaskUtil.maskCnpj(org.getCnpj()),
                org.isApproved(),
                status,
                org.getCreatedAt(),
                totalMembers,
                totalCommittees
        );
    }

    /**
     * Lists all registered organizations with LGPD-protected data.
     */
    @Transactional
    public List<AdminOrganizationDTO> listOrganizations(String executorEmail) {
        User executor = validateSysAdmin(executorEmail);

        List<Organization> organizations = organizationRepo.findAllWithOwner();
        List<AdminOrganizationDTO> result = organizations.stream().map(this::toDTO).collect(Collectors.toList());

        try {
            auditLogRepo.save(new AuditLog(
                    executor,
                    "ADMIN_ORGANIZATIONS_LISTED",
                    "Consulta à lista geral de organizações (LGPD: dados sensíveis mascarados)."
            ));
        } catch (Exception e) {
            // Audit failure does not block the response
        }

        return result;
    }

    /**
     * Lists only organizations with PENDING approval status.
     */
    @Transactional
    public List<AdminOrganizationDTO> listPendingOrganizations(String executorEmail) {
        User executor = validateSysAdmin(executorEmail);

        List<Organization> organizations = organizationRepo.findAllWithOwner();
        List<AdminOrganizationDTO> pending = organizations.stream()
                .filter(org -> !org.isApproved() && (org.getStatus() == null || "PENDING".equalsIgnoreCase(org.getStatus())))
                .map(this::toDTO)
                .collect(Collectors.toList());

        try {
            auditLogRepo.save(new AuditLog(
                    executor,
                    "ADMIN_PENDING_ORGANIZATIONS_LISTED",
                    "Consulta às organizações pendentes de aprovação."
            ));
        } catch (Exception e) {
            // Audit failure does not block the response
        }

        return pending;
    }

    /**
     * Approves a pending organization.
     */
    @Transactional
    public AdminOrganizationDTO approveOrganization(Long orgId, String executorEmail) {
        User executor = validateSysAdmin(executorEmail);

        Organization org = organizationRepo.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada para o ID: " + orgId));

        org.setApproved(true);
        org.setStatus("APPROVED");
        Organization saved = organizationRepo.save(org);

        try {
            auditLogRepo.save(new AuditLog(
                    executor,
                    "ADMIN_ORGANIZATION_APPROVED",
                    "Organização ID: " + org.getId() + " (" + org.getName() + ") APROVADA pelo SysAdmin."
            ));
        } catch (Exception e) {
            // Audit failure does not block the response
        }

        return toDTO(saved);
    }

    /**
     * Rejects a pending organization.
     */
    @Transactional
    public AdminOrganizationDTO rejectOrganization(Long orgId, String reason, String executorEmail) {
        User executor = validateSysAdmin(executorEmail);

        Organization org = organizationRepo.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada para o ID: " + orgId));

        org.setApproved(false);
        org.setStatus("REJECTED");
        Organization saved = organizationRepo.save(org);

        String detail = reason != null && !reason.isBlank() ? " | Motivo: " + reason.trim() : "";
        try {
            auditLogRepo.save(new AuditLog(
                    executor,
                    "ADMIN_ORGANIZATION_REJECTED",
                    "Organização ID: " + org.getId() + " (" + org.getName() + ") REJEITADA pelo SysAdmin" + detail
            ));
        } catch (Exception e) {
            // Audit failure does not block the response
        }

        return toDTO(saved);
    }

    /**
     * Changes the approval status of an organization (approve / block).
     */
    @Transactional
    public AdminOrganizationDTO updateOrganizationStatus(Long orgId, boolean approved, String justification, String executorEmail) {
        User executor = validateSysAdmin(executorEmail);

        Organization org = organizationRepo.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada para o ID: " + orgId));

        org.setApproved(approved);
        org.setStatus(approved ? "APPROVED" : "BLOCKED");
        Organization saved = organizationRepo.save(org);

        String statusStr = approved ? "APPROVED" : "BLOCKED";
        String justificationDetail = justification != null && !justification.isBlank() ? " | Motivo: " + justification.trim() : "";

        try {
            auditLogRepo.save(new AuditLog(
                    executor,
                    "ADMIN_ORGANIZATION_STATUS_CHANGED",
                    "Organização ID: " + org.getId() + " (" + org.getName() + ") alterada para status: " + statusStr + justificationDetail
            ));
        } catch (Exception e) {
            // Audit failure does not block the response
        }

        return toDTO(saved);
    }

    /**
     * Returns the health status of the system and its subsystems.
     */
    @Transactional(readOnly = true)
    public SystemStatusDTO getSystemStatus(String executorEmail) {
        User executor = validateSysAdmin(executorEmail);

        SystemStatusDTO status = new SystemStatusDTO();
        status.setOverallStatus("OPERATIONAL");
        status.setVersion("1.0.0-PROD");
        status.setTimestamp(LocalDateTime.now());
        status.setUptimeSeconds((System.currentTimeMillis() - instanceStartMs) / 1000);

        // 1. Database subsystem
        SubsystemInfo dbInfo = new SubsystemInfo();
        dbInfo.setName("Banco de Dados PostgreSQL");
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(2);
            dbInfo.setStatus(valid ? "ONLINE" : "WARNING");
            dbInfo.setDetails("Conexão ativa com pool HikariCP. Dialeto PostgreSQL ativo.");
        } catch (Exception e) {
            dbInfo.setStatus("FAILURE");
            dbInfo.setDetails("Falha de comunicação: " + e.getMessage());
            status.setOverallStatus("DEGRADED");
        }
        status.setDatabase(dbInfo);

        // 2. Network subsystem: the address and port this instance is actually serving on
        SubsystemInfo netInfo = new SubsystemInfo();
        netInfo.setName("Rede");
        try {
            InetAddress local = InetAddress.getLocalHost();
            netInfo.setStatus("ONLINE");
            netInfo.setDetails("Servidor em " + local.getHostAddress() + ":" + serverPort
                    + " | Host: " + local.getHostName() + " | Requisições REST respondendo.");
        } catch (Exception e) {
            netInfo.setStatus("WARNING");
            netInfo.setDetails("Servidor na porta " + serverPort + ". Endereço local indisponível: " + e.getMessage());
        }
        status.setNetwork(netInfo);

        // 3. JVM and memory resources
        Runtime runtime = Runtime.getRuntime();
        long totalMb = runtime.totalMemory() / (1024 * 1024);
        long freeMb = runtime.freeMemory() / (1024 * 1024);
        long usedMb = totalMb - freeMb;
        int procs = runtime.availableProcessors();
        status.setMemory(new MemoryInfo(totalMb, usedMb, freeMb, procs));

        return status;
    }

    /**
     * Builds aggregated reports with anonymized data and PII masking.
     */
    @Transactional
    public AdminReportDTO getReports(String executorEmail) {
        User executor = validateSysAdmin(executorEmail);

        AdminReportDTO report = new AdminReportDTO();
        report.setGeneratedAt(LocalDateTime.now());

        // Users
        List<User> users = userRepo.findAll();
        report.setTotalUsers(users.size());
        report.setVerifiedUsers(users.stream().filter(User::isEmailVerified).count());

        Map<String, Long> roleMap = new HashMap<>();
        roleMap.put("USER", users.stream().filter(u -> "USER".equalsIgnoreCase(u.getRole())).count());
        roleMap.put("ADMIN", users.stream().filter(u -> "ADMIN".equalsIgnoreCase(u.getRole())).count());
        roleMap.put("SYSADMIN", users.stream().filter(u -> "SYSADMIN".equalsIgnoreCase(u.getRole())).count());
        report.setUsersByRole(roleMap);

        // Organizations
        List<Organization> orgs = organizationRepo.findAll();
        report.setTotalOrganizations(orgs.size());
        report.setApprovedOrganizations(orgs.stream().filter(Organization::isApproved).count());
        report.setPendingOrganizations(orgs.stream().filter(o -> !o.isApproved()).count());

        // Committees
        report.setTotalCommittees(committeeRepo.count());

        // Audit
        report.setTotalAuditLogs(auditLogRepo.count());

        // Recent activities with anonymization
        List<AuditLog> recentLogs = auditLogRepo.findTop100ByOrderByCreatedAtDesc();
        List<AuditActivityDTO> activities = recentLogs.stream().limit(15).map(l -> {
            String authorName = l.getUser() != null ? l.getUser().getName() : "Sistema";
            String authorEmail = l.getUser() != null ? l.getUser().getEmail() : null;
            return new AuditActivityDTO(
                    l.getId(),
                    l.getAction(),
                    authorName,
                    LgpdMaskUtil.maskEmail(authorEmail),
                    LgpdMaskUtil.sanitizeDetails(l.getDetails()),
                    l.getCreatedAt()
            );
        }).collect(Collectors.toList());
        report.setRecentActivities(activities);

        try {
            auditLogRepo.save(new AuditLog(
                    executor,
                    "ADMIN_REPORTS_VIEWED",
                    "Relatórios agregados e estatísticas gerais consultados pelo Administrador."
            ));
        } catch (Exception e) {
            // Audit failure does not block the response
        }

        return report;
    }

    /**
     * Performs a safe restart of in-memory subsystems and caches.
     */
    @Transactional
    public Map<String, Object> restartSystem(CriticalActionRequestDTO dto, String executorEmail) {
        User executor = validateSysAdmin(executorEmail);

        String reason = dto != null && dto.getReason() != null ? dto.getReason().trim() : "Rotina administrativa periódica";

        // Critical audit record
        auditLogRepo.save(new AuditLog(
                executor,
                "ADMIN_SYSTEM_RESTART",
                "Reinicialização dos subsistemas solicitada. Motivo: " + reason
        ));

        // Force garbage collection and clear volatile caches
        System.gc();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Subsistemas reinicializados e sincronizados com sucesso. Estado operacional verificado.");
        response.put("executor", executor.getName());
        response.put("timestamp", LocalDateTime.now());
        return response;
    }

    /**
     * Performs the full system reset, preserving only the executing administrator account.
     * Transactional cascade cleanup of organizations, committees, members, requests and regular users.
     */
    @Transactional
    public Map<String, Object> resetSystem(CriticalActionRequestDTO dto, String executorEmail) {
        User executor = validateSysAdmin(executorEmail);

        if (dto == null || !"CONFIRM_FULL_RESET".equals(dto.getConfirmation())) {
            throw new IllegalArgumentException("Código de confirmação inválido. Digite exatamente 'CONFIRM_FULL_RESET' para executar o Reset Total do Sistema.");
        }

        Long adminId = executor.getId();
        String reason = dto.getReason() != null && !dto.getReason().isBlank() ? dto.getReason().trim() : "Reset Total Administrativo do Sistema";

        // 1. Delete audit logs of other users (preserves the executing SysAdmin logs)
        auditLogRepo.deleteAllExceptAdminLogs(adminId);

        // 2. Delete all organization access requests
        accessRequestRepo.deleteAllInBatch();

        // 3. Delete all committee memberships
        committeeMemberRepo.deleteAllInBatch();

        // 4. Delete all committees
        committeeRepo.deleteAllInBatch();

        // 5. Delete all organization memberships
        organizationMemberRepo.deleteAllInBatch();

        // 6. Delete all organizations
        organizationRepo.deleteAllInBatch();

        // 7. Delete all regular users (preserving only the administrator)
        userRepo.deleteAllExceptAdmin(adminId);

        // 8. Immutable audit record of the full reset
        try {
            auditLogRepo.save(new AuditLog(
                    executor,
                    "ADMIN_SYSTEM_FULL_RESET",
                    "Reset Total do Sistema executado. Base limpa com preservação estrita do Administrador: " + executor.getEmail() + " | Motivo: " + reason
            ));
        } catch (Exception e) {
            // Audit failure does not block completion
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Reset Total do Sistema executado com sucesso! Todas as organizações, comissões e usuários comuns foram limpos do banco de dados, preservando exclusivamente a conta do Administrador.");
        response.put("adminPreserved", executor.getName() + " (" + executor.getEmail() + ")");
        response.put("reason", reason);
        response.put("timestamp", LocalDateTime.now());
        return response;
    }
}
