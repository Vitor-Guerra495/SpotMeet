package com.spotmeet.backend.service;

import com.spotmeet.backend.dto.AdminOrganizationDTO;
import com.spotmeet.backend.dto.AdminReportDTO;
import com.spotmeet.backend.dto.AdminReportDTO.AuditActivityDTO;
import com.spotmeet.backend.dto.CriticalActionRequestDTO;
import com.spotmeet.backend.dto.SystemStatusDTO;
import com.spotmeet.backend.dto.SystemDetailsDTO;
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
import com.spotmeet.backend.repository.OrganizationInvitationRepository;
import com.spotmeet.backend.repository.OrganizationRepository;
import com.spotmeet.backend.repository.UserRepository;
import com.spotmeet.backend.security.LgpdMaskUtil;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * System administration service.
 * Implements LGPD compliance, masking and auditing of critical actions.
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

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
    private OrganizationInvitationRepository invitationRepo;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private SystemMonitoringService monitoringService;

    @Autowired
    private ApplicationContext context;

    private volatile LocalDateTime lastNetworkRestart;

    @Value("${server.port:8080}")
    private String serverPort;

    /**
     * Validates the executor permission (must be ADMIN or SYSADMIN).
     */
    private User validateSysAdmin(String executorEmail) {
        return validateSysAdmin(executorEmail, false);
    }

    private User validateSysAdmin(String executorEmail, boolean lockUser) {
        User user = (lockUser ? userRepo.findByEmailForUpdate(executorEmail) : userRepo.findByEmail(executorEmail))
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

        AdminOrganizationDTO dto = new AdminOrganizationDTO(
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
        dto.setModerationReason(org.getModerationReason());
        dto.setResubmissionNote(org.getResubmissionNote());
        dto.setResubmittedAt(org.getResubmittedAt());
        return dto;
    }

    /**
     * Lists all registered organizations with LGPD-protected data.
     */
    @Transactional
    public List<AdminOrganizationDTO> listOrganizations(String executorEmail) {
        validateSysAdmin(executorEmail);

        List<Organization> organizations = organizationRepo.findAllWithOwner();
        List<AdminOrganizationDTO> result = organizations.stream().map(this::toDTO).collect(Collectors.toList());

        return result;
    }

    /**
     * Lists only organizations with PENDING approval status.
     */
    @Transactional
    public List<AdminOrganizationDTO> listPendingOrganizations(String executorEmail) {
        validateSysAdmin(executorEmail);

        List<Organization> organizations = organizationRepo.findAllWithOwner();
        List<AdminOrganizationDTO> pending = organizations.stream()
                .filter(org -> !org.isApproved() && (org.getStatus() == null || "PENDING".equalsIgnoreCase(org.getStatus())))
                .map(this::toDTO)
                .collect(Collectors.toList());

        return pending;
    }

    /**
     * Approves a pending organization.
     */
    @Transactional
    public AdminOrganizationDTO approveOrganization(Long orgId, String executorEmail) {
        User executor = validateSysAdmin(executorEmail);

        Organization org = organizationRepo.findByIdForUpdate(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada para o ID: " + orgId));

        org.setApproved(true);
        org.setStatus("APPROVED");
        org.setModerationReason(null);
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

        Organization org = organizationRepo.findByIdForUpdate(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada para o ID: " + orgId));

        String cleanReason = validateModerationReason(reason);
        org.setApproved(false);
        org.setStatus("REJECTED");
        org.setModerationReason(cleanReason);
        Organization saved = organizationRepo.save(org);

        String detail = " | Motivo informado (" + cleanReason.length() + " caracteres)";
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

        Organization org = organizationRepo.findByIdForUpdate(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada para o ID: " + orgId));

        String cleanReason = approved ? null : validateModerationReason(justification);
        String newStatus = approved ? "APPROVED" : "BLOCKED";
        org.setApproved(approved);
        org.setStatus(newStatus);
        org.setModerationReason(cleanReason);
        Organization saved = organizationRepo.save(org);

        String statusStr = approved ? "APPROVED" : "BLOCKED";
        String justificationDetail = cleanReason == null ? "" : " | Motivo informado (" + cleanReason.length() + " caracteres)";

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

    private String validateModerationReason(String reason) {
        String clean = reason == null ? "" : reason.replaceAll(com.spotmeet.backend.dto.ValidationPatterns.NAME_WHITESPACE, "");
        if (clean.length() < 3 || clean.length() > 500
                || clean.codePoints().anyMatch(value -> Character.isISOControl(value) && value != 10 && value != 9)) {
            throw new com.spotmeet.backend.exception.InvalidModerationReasonException();
        }
        return clean;
    }

    /**
     * Returns the health status of the system and its subsystems.
     */
    @Transactional(readOnly = true)
    public SystemStatusDTO getSystemStatus(String executorEmail) {
        validateSysAdmin(executorEmail);

        SystemStatusDTO status = new SystemStatusDTO();
        status.setVersion(monitoringService.getVersion());
        status.setTimestamp(LocalDateTime.now());
        status.setUptimeSeconds(monitoringService.getUptimeSeconds());

        SubsystemInfo dbInfo = new SubsystemInfo();
        dbInfo.setName("Banco de Dados PostgreSQL");
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(2);
            dbInfo.setStatus(valid ? "ONLINE" : "WARNING");
            dbInfo.setDetails(valid ? "Conexão com o banco validada."
                    : "A conexão com o banco não passou na validação.");
        } catch (Exception e) {
            dbInfo.setStatus("FAILURE");
            dbInfo.setDetails("Não foi possível consultar o banco de dados.");
        }
        status.setDatabase(dbInfo);

        SubsystemInfo netInfo = buildNetworkInfo();
        status.setNetwork(netInfo);

        MemoryInfo resources = monitoringService.collectResources();
        status.setMemory(resources);
        status.setOverallStatus(monitoringService.getOverallStatus(dbInfo.getStatus(), netInfo.getStatus(), resources.getStatus()));

        return status;
    }

    public SystemDetailsDTO getSystemDetails(String executorEmail) {
        validateSysAdmin(executorEmail);
        return monitoringService.collect();
    }

    /**
     * Network status: tries to connect to the server port and lists the IPv4 addresses of the active interfaces.
     */
    private SubsystemInfo buildNetworkInfo() {
        SubsystemInfo info = new SubsystemInfo();
        info.setName("Rede");

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", Integer.parseInt(serverPort)), 1000);
        } catch (Exception e) {
            info.setStatus("FAILURE");
            info.setDetails("A porta " + serverPort + " não aceita conexões: " + e.getMessage());
            return info;
        }

        String lastRestart = lastNetworkRestart == null
                ? "sem reinício desde que o servidor iniciou"
                : "último reinício em " + lastNetworkRestart.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));

        List<String> addresses = listNetworkAddresses();
        if (addresses.isEmpty()) {
            info.setStatus("WARNING");
            info.setDetails("Porta " + serverPort + " ativa, mas sem placa de rede conectada: só este computador acessa. "
                    + lastRestart + ".");
        } else {
            info.setStatus("ONLINE");
            info.setDetails("Porta " + serverPort + " aceitando conexões em " + String.join(", ", addresses)
                    + " | " + lastRestart + ".");
        }
        return info;
    }

    private List<String> listNetworkAddresses() {
        List<String> addresses = new ArrayList<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback()) {
                    continue;
                }
                for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLinkLocalAddress()) {
                        addresses.add(address.getHostAddress() + " (" + nic.getName() + ")");
                    }
                }
            }
        } catch (SocketException e) {
            log.warn("Não foi possível listar as interfaces de rede: {}", e.getMessage());
        }
        return addresses;
    }

    /**
     * Builds aggregated reports with anonymized data and PII masking.
     */
    @Transactional
    public AdminReportDTO getReports(String executorEmail) {
        validateSysAdmin(executorEmail);

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

        return report;
    }

    /**
     * Restarts one subsystem chosen by the administrator: DATABASE, NETWORK or RESOURCES.
     */
    @Transactional
    public Map<String, Object> restartSystem(CriticalActionRequestDTO dto, String executorEmail) {
        User executor = validateSysAdmin(executorEmail);

        String subsystem = dto != null && dto.getSubsystem() != null ? dto.getSubsystem().trim().toUpperCase() : "";
        String reason = dto != null && dto.getReason() != null ? dto.getReason().trim() : "Rotina administrativa periódica";

        String result = switch (subsystem) {
            case "DATABASE" -> restartDatabase();
            case "NETWORK" -> restartNetwork();
            case "RESOURCES" -> restartResources();
            default -> throw new IllegalArgumentException("Subsistema inválido. Escolha Banco de Dados, Rede ou Recursos.");
        };

        auditLogRepo.save(new AuditLog(
                executor,
                "ADMIN_SYSTEM_RESTART",
                "Subsistema reiniciado: " + subsystem + ". Motivo: " + reason
        ));

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("subsystem", subsystem);
        response.put("message", result);
        response.put("executor", executor.getName());
        response.put("timestamp", LocalDateTime.now());
        return response;
    }

    /**
     * Database: discards the current pool connections so new ones are opened, then tests the connection.
     */
    private String restartDatabase() {
        try {
            if (dataSource.isWrapperFor(HikariDataSource.class)) {
                dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().softEvictConnections();
            }
            try (Connection conn = dataSource.getConnection()) {
                if (!conn.isValid(2)) {
                    throw new IllegalStateException("A conexão com o banco não respondeu após a reinicialização.");
                }
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("Falha ao reiniciar o banco de dados: " + e.getMessage());
        }
        return "Banco de dados reiniciado: conexões renovadas e conexão testada com sucesso.";
    }

    /**
     * Network: closes the HTTP connector (the Tomcat component that listens on the server port) and opens it again.
     * It runs in another thread after a short delay, so this response is sent before the connection is cut.
     */
    private String restartNetwork() {
        if (!(context instanceof WebServerApplicationContext webContext)
                || !(webContext.getWebServer() instanceof TomcatWebServer tomcat)) {
            throw new IllegalStateException("O servidor web não é o Tomcat embutido, a rede não pode ser reiniciada.");
        }
        Connector[] connectors = tomcat.getTomcat().getService().findConnectors();
        new Thread(() -> reopenConnectors(connectors), "network-restart").start();
        return "Rede em reinicialização: a porta " + serverPort + " será fechada e reaberta em cerca de 1 segundo. "
                + "Requisições em andamento nesse instante podem falhar.";
    }

    private synchronized void reopenConnectors(Connector[] connectors) {
        try {
            Thread.sleep(1000);
            for (Connector connector : connectors) {
                connector.stop();
                startConnector(connector);
            }
            lastNetworkRestart = LocalDateTime.now();
            log.info("Rede reiniciada: conector HTTP reaberto na porta {}.", serverPort);
        } catch (Exception e) {
            log.error("Falha ao reiniciar a rede, o servidor pode ficar sem responder: {}", e.getMessage());
        }
    }

    private void startConnector(Connector connector) throws LifecycleException, InterruptedException {
        for (int attempt = 1; ; attempt++) {
            try {
                connector.start();
                return;
            } catch (LifecycleException e) {
                if (attempt == 3) {
                    throw e;
                }
                Thread.sleep(1000);
            }
        }
    }

    /**
     * Resources: runs the garbage collector and reports how much memory was released.
     */
    private String restartResources() {
        Runtime runtime = Runtime.getRuntime();
        long usedBefore = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        System.gc();
        long usedAfter = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long released = Math.max(0, usedBefore - usedAfter);
        return "Recursos reiniciados: " + released + " MB de memória liberados (em uso: " + usedAfter + " MB).";
    }

    /**
     * Performs the full system reset, preserving only the executing administrator account.
     * Transactional cascade cleanup of organizations, committees, members, requests and regular users.
     */
    @Transactional
    public Map<String, Object> resetSystem(CriticalActionRequestDTO dto, String executorEmail) {
        User executor = validateSysAdmin(executorEmail, true);

        if (dto == null || !"CONFIRM_FULL_RESET".equals(dto.getConfirmation())) {
            throw new IllegalArgumentException("Código de confirmação inválido. Digite exatamente 'CONFIRM_FULL_RESET' para executar o Reset Total do Sistema.");
        }

        Long adminId = executor.getId();
        String reason = dto.getReason() != null && !dto.getReason().isBlank() ? dto.getReason().trim() : "Reset Total Administrativo do Sistema";

        // 1. Delete audit logs of other users (preserves the executing SysAdmin logs)
        auditLogRepo.deleteAllExceptAdminLogs(adminId);

        invitationRepo.deleteAllInBatch();

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

        executor.setTokensValidAfter(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        userRepo.save(executor);

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
