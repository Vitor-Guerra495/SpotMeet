package com.spotmeet.backend;

import com.spotmeet.backend.controller.CommitteeController;
import com.spotmeet.backend.dto.CommitteeRequestDTO;
import com.spotmeet.backend.dto.ValidationPatterns;
import com.spotmeet.backend.exception.GlobalExceptionHandler;
import com.spotmeet.backend.model.*;
import com.spotmeet.backend.model.OrganizationMember.OrganizationRole;
import com.spotmeet.backend.repository.*;
import com.spotmeet.backend.service.CommitteeService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Controllers and services are real; repository behavior is simulated.
class CommitteeRulesTests {
    private CommitteeRepository committees;
    private OrganizationRepository organizations;
    private OrganizationMemberRepository memberships;
    private User leader;
    private Organization first;
    private Committee existing;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        committees = mock(CommitteeRepository.class);
        organizations = mock(OrganizationRepository.class);
        memberships = mock(OrganizationMemberRepository.class);
        UserRepository users = mock(UserRepository.class);
        leader = new User(); leader.setId(1L); leader.setEmail("leader@example.com"); leader.setName("Líder"); leader.setRole("USER");
        first = new Organization(); first.setId(1L); first.setName("Equipe"); first.setOwner(leader); first.setApproved(true);
        Organization second = new Organization(); second.setId(2L); second.setName("Outra equipe"); second.setOwner(leader); second.setApproved(true);
        existing = new Committee("RH", "Original", first); existing.setId(10L);
        when(users.findByEmail(anyString())).thenReturn(Optional.of(leader));
        when(organizations.findById(anyLong())).thenAnswer(call -> Optional.of(call.getArgument(0).equals(1L) ? first : second));
        when(organizations.findByIdForUpdate(anyLong())).thenAnswer(call -> Optional.of(call.getArgument(0).equals(1L) ? first : second));
        when(committees.findById(10L)).thenReturn(Optional.of(existing));
        when(committees.existsByNormalizedName(anyLong(), anyString(), eq(ValidationPatterns.ORGANIZATION_WHITESPACE), nullable(Long.class)))
            .thenAnswer(call -> call.getArgument(0).equals(1L) && !Objects.equals(call.getArgument(3), 10L)
                && ValidationPatterns.normalizeOrganizationName(call.getArgument(1)).equalsIgnoreCase("RH"));
        when(committees.save(any())).thenAnswer(call -> { Committee value = call.getArgument(0); if (value.getId() == null) value.setId(20L); return value; });
        CommitteeService service = new CommitteeService();
        ReflectionTestUtils.setField(service, "committeeRepo", committees);
        ReflectionTestUtils.setField(service, "organizationRepo", organizations);
        ReflectionTestUtils.setField(service, "organizationMemberRepo", memberships);
        ReflectionTestUtils.setField(service, "userRepo", users);
        ReflectionTestUtils.setField(service, "committeeMemberRepo", mock(CommitteeMemberRepository.class));
        ReflectionTestUtils.setField(service, "auditLogRepo", mock(AuditLogRepository.class));
        CommitteeController controller = new CommitteeController(); ReflectionTestUtils.setField(controller, "committeeService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private UsernamePasswordAuthenticationToken principal() { return new UsernamePasswordAuthenticationToken(leader.getEmail(), "test", List.of()); }

    @ParameterizedTest
    @ValueSource(strings = {"Comissão de Eventos 2", "ComunicAÇÃO", "Área comum", "TI_Dev", "RH-Geral", "  Equipe   de  Apoio  ", "Equipe\tde\nApoio", "\u00a0Área\u2009\u2009Comum\u3000", "\u0085rH\ufeff", "Áá", "123"})
    void normalizesNamesBeforeDtoAndEntityValidation(String name) {
        CommitteeRequestDTO dto = new CommitteeRequestDTO(name, null);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(dto).isEmpty());
            Committee committee = new Committee(name, null, first);
            assertTrue(factory.getValidator().validate(committee).isEmpty());
            assertEquals(ValidationPatterns.normalizeOrganizationName(name), dto.getName());
            assertEquals(dto.getName(), committee.getName());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "a", "Ação <script>", "Nome' OR 1=1", "Comissão/Outro", "Nome@Equipe", "Nome\u200bOculto", "\ud83d\ude00"})
    void rejectsMalformedNamesAtTheHttpBoundary(String name) throws Exception {
        String body = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("name", name));
        mvc.perform(post("/api/organizations/1/committees").principal(principal()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verify(committees, never()).save(any());
    }

    @Test
    void respectsTheNormalizedLengthLimit() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(new CommitteeRequestDTO("  " + "a".repeat(50) + "  ", null)).isEmpty());
            assertFalse(factory.getValidator().validate(new CommitteeRequestDTO("a".repeat(51), null)).isEmpty());
        }
    }

    @Test
    void rejectsCaseAndWhitespaceDuplicateOnCreateAndEdit() throws Exception {
        mvc.perform(post("/api/organizations/1/committees").principal(principal()).contentType("application/json").content("{\"name\":\"  rH  \"}")).andExpect(status().isConflict());
        Committee other = new Committee("Eventos", null, first); other.setId(11L); when(committees.findById(11L)).thenReturn(Optional.of(other));
        mvc.perform(put("/api/committees/11").principal(principal()).contentType("application/json").content("{\"name\":\"rh\"}")).andExpect(status().isConflict());
        assertEquals("Eventos", other.getName()); verify(committees, never()).save(any());
    }

    @Test
    void allowsOwnSpellingChangeAndSameNameInAnotherOrganization() throws Exception {
        mvc.perform(put("/api/committees/10").principal(principal()).contentType("application/json").content("{\"name\":\"rh\"}")).andExpect(status().isOk());
        assertEquals("rh", existing.getName());
        mvc.perform(post("/api/organizations/2/committees").principal(principal()).contentType("application/json").content("{\"name\":\"RH\"}")).andExpect(status().isCreated());
        verify(organizations).findByIdForUpdate(1L); verify(organizations).findByIdForUpdate(2L);
    }

    @Test
    void savesNormalizedNameAndOnlyEditableFields() throws Exception {
        mvc.perform(post("/api/organizations/1/committees").principal(principal()).contentType("application/json")
            .content("{\"name\":\"  Comissão   de Eventos 2  \",\"description\":\"  Finalidade  \",\"organizationId\":2,\"role\":\"SYSADMIN\"}")).andExpect(status().isCreated());
        var saved = org.mockito.ArgumentCaptor.forClass(Committee.class); verify(committees).save(saved.capture());
        assertEquals("Comissão de Eventos 2", saved.getValue().getName()); assertSame(first, saved.getValue().getOrganization()); assertEquals("Finalidade", saved.getValue().getDescription());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "OTHER_ORGANIZATION", "ADMIN"})
    void normalizedNamesNeverBypassObjectAuthorization(String role) throws Exception {
        User owner = new User(); owner.setId(2L); first.setOwner(owner);
        if (role.equals("ADMIN")) leader.setRole("ADMIN");
        OrganizationMember membership = new OrganizationMember(); membership.setRole(OrganizationRole.MEMBER);
        when(memberships.findByUserIdAndOrganizationId(1L, 1L)).thenReturn(role.equals("OTHER_ORGANIZATION") ? Optional.empty() : Optional.of(membership));
        mvc.perform(post("/api/organizations/1/committees").principal(principal()).contentType("application/json").content("{\"name\":\"Comissão de Eventos\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/committees/10").principal(principal()).contentType("application/json").content("{\"name\":\"Comissão de Eventos\"}")).andExpect(status().isForbidden());
        verify(committees, never()).save(any()); assertEquals("RH", existing.getName());
    }
}
