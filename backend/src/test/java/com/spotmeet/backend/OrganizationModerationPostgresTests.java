package com.spotmeet.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotmeet.backend.dto.CriticalActionRequestDTO;
import com.spotmeet.backend.dto.OrganizationRequestDTO;
import com.spotmeet.backend.model.OrganizationMember;
import com.spotmeet.backend.model.User;
import com.spotmeet.backend.repository.OrganizationMemberRepository;
import com.spotmeet.backend.repository.OrganizationRepository;
import com.spotmeet.backend.repository.UserRepository;
import com.spotmeet.backend.security.JwtUtil;
import com.spotmeet.backend.service.AdminService;
import com.spotmeet.backend.service.OrganizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Base64;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "SPOTMEET_MODERATION_QA", matches = "1")
@SpringBootTest(properties = {"spring.mail.username=", "spring.mail.password=", "spring.jpa.hibernate.ddl-auto=update"})
@AutoConfigureMockMvc
class OrganizationModerationPostgresTests {
    @DynamicPropertySource static void isolatedDatabase(DynamicPropertyRegistry properties) {
        String url = System.getenv("SPOTMEET_QA_DB_URL");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/spotmeet_lote7_qa")) {
            throw new IllegalStateException("An isolated QA database is required.");
        }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> "postgres");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spotmeet.jwt.secret", () -> Base64.getEncoder().encodeToString("isolated-moderation-fixture-key-123456".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    @MockBean(name = "sysAdminBootstrap") ApplicationRunner bootstrap;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate sql;
    @Autowired UserRepository users;
    @Autowired OrganizationMemberRepository members;
    @Autowired OrganizationService organizations;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired AdminService adminService;
    @Autowired JwtUtil jwt;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    private User user(String name, String role) {
        User user = new User(); user.setName(name); user.setEmail(name + "@example.com"); user.setRole(role);
        user.setPassword("unused-fixture-password"); user.setEmailVerified(true); return users.save(user);
    }
    private String authorization(User user) { return "Bearer " + jwt.generateToken(user.getEmail(), user.getRole()); }
    private long count(String table) {
        if (!java.util.Set.of("organizations", "users").contains(table)) throw new IllegalArgumentException();
        return sql.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    @Test void persistsPrivateReasonsAndPreservesAccessRequestsRollbackAndReset() throws Exception {
        assertEquals(0, count("users"));
        assertEquals(9, sql.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'", Integer.class));
        User admin = user("qaadmin", "SYSADMIN"), legacyAdmin = user("qaadministrator", "ADMIN"), owner = user("qaowner", "USER"), vice = user("qavice", "USER"), applicant = user("qaapplicant", "USER");
        String ownerAuth = authorization(owner), adminAuth = authorization(admin), applicantAuth = authorization(applicant);
        mvc.perform(get("/api/organizations/mine")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/organizations").header("Authorization", ownerAuth).contentType("application/json")
                .content("{\"name\":\"Equipe Integrada\",\"accessKey\":\"#Integrada\",\"ownerId\":999,\"approved\":true}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.approved").value(false)).andExpect(jsonPath("$.ownerId").value(owner.getId()));
        long orgId = sql.queryForObject("SELECT id FROM organizations WHERE access_key = '#Integrada'", Long.class);
        mvc.perform(post("/api/admin/organizations/" + orgId + "/reject").header("Authorization", applicantAuth).contentType("application/json").content("{\"reason\":\"Inválido\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/organizations/" + orgId + "/reject").header("Authorization", adminAuth).contentType("application/json").content("{\"reason\":\"ab\"}"))
                .andExpect(status().isBadRequest());
        String reason = "Dados incompletos, confira a documentação.";
        mvc.perform(post("/api/admin/organizations/" + orgId + "/reject").header("Authorization", adminAuth).contentType("application/json")
                .content(mapper.writeValueAsString(java.util.Map.of("reason", reason, "ownerId", applicant.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.moderationReason").value(reason));
        assertEquals(reason, sql.queryForObject("SELECT moderation_reason FROM organizations WHERE id=?", String.class, orgId));
        OrganizationMember membership = new OrganizationMember(); membership.setUser(vice); membership.setOrganization(organizationRepository.findById(orgId).orElseThrow()); membership.setRole(OrganizationMember.OrganizationRole.ORG_VICE_OWNER); members.save(membership);
        mvc.perform(get("/api/organizations/mine").header("Authorization", ownerAuth)).andExpect(status().isOk()).andExpect(jsonPath("$[0].moderationReason").value(reason));
        mvc.perform(get("/api/organizations/mine").header("Authorization", authorization(vice))).andExpect(status().isOk()).andExpect(jsonPath("$[0].moderationReason").isEmpty());
        mvc.perform(get("/api/admin/organizations").header("Authorization", authorization(legacyAdmin))).andExpect(status().isOk()).andExpect(jsonPath("$[0].moderationReason").value(reason));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Void> approve = () -> { start.await(); adminService.approveOrganization(orgId, admin.getEmail()); return null; };
            Future<Void> first = executor.submit(approve), second = executor.submit(approve); start.countDown(); first.get(20, TimeUnit.SECONDS); second.get(20, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        mvc.perform(get("/api/organizations/mine").header("Authorization", ownerAuth)).andExpect(jsonPath("$[0].moderationReason").isEmpty());
        mvc.perform(post("/api/access-requests").header("Authorization", applicantAuth).contentType("application/json").content("{\"accessKey\":\"#Integrada\",\"ownerId\":999}"))
                .andExpect(status().isCreated());
        long requestId = sql.queryForObject("SELECT id FROM access_requests", Long.class);
        mvc.perform(post("/api/access-requests").header("Authorization", applicantAuth).contentType("application/json").content("{\"accessKey\":\"#Integrada\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/access-requests/" + requestId + "/approve").header("Authorization", ownerAuth)).andExpect(status().isOk());
        long organizationsBefore = count("organizations");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            OrganizationRequestDTO request = new OrganizationRequestDTO(); request.setName("Equipe Revertida"); request.setAccessKey("#Revertida");
            organizations.createOrganization(owner.getEmail(), request); status.setRollbackOnly();
        });
        assertEquals(organizationsBefore, count("organizations"));
        assertEquals(500, sql.queryForObject("SELECT character_maximum_length FROM information_schema.columns WHERE table_name='organizations' AND column_name='moderation_reason'", Integer.class));
        assertEquals("YES", sql.queryForObject("SELECT is_nullable FROM information_schema.columns WHERE table_name='organizations' AND column_name='moderation_reason'", String.class));
        organizations.deleteOrganization(orgId, owner.getEmail()); assertEquals(0, count("organizations"));
        CriticalActionRequestDTO reset = new CriticalActionRequestDTO(); reset.setConfirmation("CONFIRM_FULL_RESET"); reset.setReason("QA isolado"); adminService.resetSystem(reset, admin.getEmail());
        assertEquals(1, count("users"));
        mvc.perform(get("/api/organizations/mine").header("Authorization", adminAuth)).andExpect(status().isUnauthorized());
    }
}
