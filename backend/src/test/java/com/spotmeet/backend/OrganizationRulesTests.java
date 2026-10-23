package com.spotmeet.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotmeet.backend.controller.MembershipController;
import com.spotmeet.backend.controller.OrganizationController;
import com.spotmeet.backend.dto.OrganizationRequestDTO;
import com.spotmeet.backend.dto.ValidationPatterns;
import com.spotmeet.backend.exception.GlobalExceptionHandler;
import com.spotmeet.backend.model.*;
import com.spotmeet.backend.model.OrganizationMember.OrganizationRole;
import com.spotmeet.backend.repository.*;
import com.spotmeet.backend.service.MembershipService;
import com.spotmeet.backend.service.OrganizationService;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Real services and controllers; persistence is simulated here and verified separately on PostgreSQL.
class OrganizationRulesTests {
    private final Map<Long, Organization> organizations = new LinkedHashMap<>();
    private final Map<String, User> users = new HashMap<>();
    private final Map<String, OrganizationMember> memberships = new HashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private OrganizationRepository repository;
    private OrganizationMemberRepository members;
    private AccessRequestRepository requests;
    private AuditLogRepository audit;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repository = mock(OrganizationRepository.class);
        members = mock(OrganizationMemberRepository.class);
        requests = mock(AccessRequestRepository.class);
        audit = mock(AuditLogRepository.class);
        UserRepository people = mock(UserRepository.class);
        when(people.findByEmail(anyString())).thenAnswer(call -> Optional.ofNullable(users.get(call.getArgument(0))));
        for (String identity : new String[]{"owner", "vice", "subowner", "member", "outsider", "sysadmin", "admin"}) {
            User user = new User();
            user.setId((long) users.size() + 1);
            user.setName("Test " + identity);
            user.setEmail(identity + "@example.com");
            user.setRole(identity.equals("sysadmin") ? "SYSADMIN" : identity.equals("admin") ? "ADMIN" : "USER");
            users.put(user.getEmail(), user);
        }
        Organization target = organization(1L, "Equipe Original", "#Alpha", "owner@example.com");
        target.setCnpj("12.345.678/0001-99");
        organization(2L, "Apoio Social", "#Beta", "outsider@example.com");
        member("vice", OrganizationRole.ORG_VICE_OWNER);
        member("subowner", OrganizationRole.ORG_SUBOWNER);
        member("member", OrganizationRole.MEMBER);
        when(members.findByUserIdAndOrganizationId(anyLong(), anyLong())).thenAnswer(call -> Optional.ofNullable(memberships.get(call.getArgument(0) + ":" + call.getArgument(1))));
        when(members.existsByUserIdAndOrganizationId(anyLong(), anyLong())).thenAnswer(call -> memberships.containsKey(call.getArgument(0) + ":" + call.getArgument(1)));
        when(repository.findById(anyLong())).thenAnswer(call -> Optional.ofNullable(organizations.get(call.getArgument(0))));
        when(repository.findByIdForUpdate(anyLong())).thenAnswer(call -> Optional.ofNullable(organizations.get(call.getArgument(0))));
        when(repository.existsByNormalizedName(anyString(), eq(ValidationPatterns.ORGANIZATION_WHITESPACE), nullable(Long.class))).thenAnswer(call ->
                organizations.values().stream().anyMatch(org -> !Objects.equals(org.getId(), call.getArgument(2)) && folded(org.getName()).equals(folded(call.getArgument(0)))));
        when(repository.existsByNormalizedAccessKey(anyString(), eq(ValidationPatterns.ORGANIZATION_WHITESPACE), nullable(Long.class))).thenAnswer(call ->
                organizations.values().stream().anyMatch(org -> !Objects.equals(org.getId(), call.getArgument(2)) && folded(org.getAccessKey()).equals(folded(call.getArgument(0)))));
        when(repository.existsByCnpjExcludingId(anyString(), nullable(Long.class))).thenAnswer(call ->
                organizations.values().stream().anyMatch(org -> !Objects.equals(org.getId(), call.getArgument(1)) && Objects.equals(org.getCnpj(), call.getArgument(0))));
        when(repository.findIdsByAccessKeyIgnoreCase(anyString(), eq(ValidationPatterns.ORGANIZATION_WHITESPACE))).thenAnswer(call ->
                organizations.values().stream().filter(org -> folded(org.getAccessKey()).equals(folded(call.getArgument(0)))).limit(2).map(Organization::getId).toList());
        when(repository.save(any(Organization.class))).thenAnswer(call -> {
            Organization org = call.getArgument(0);
            if (org.getId() == null) org.setId((long) organizations.size() + 1);
            organizations.put(org.getId(), org);
            return org;
        });
        when(requests.save(any(AccessRequest.class))).thenAnswer(call -> {
            AccessRequest request = call.getArgument(0);
            request.setId(1L);
            return request;
        });
        OrganizationService service = new OrganizationService();
        ReflectionTestUtils.setField(service, "organizationRepo", repository);
        ReflectionTestUtils.setField(service, "userRepo", people);
        ReflectionTestUtils.setField(service, "organizationMemberRepo", members);
        ReflectionTestUtils.setField(service, "auditLogRepo", audit);
        MembershipService membershipService = new MembershipService();
        ReflectionTestUtils.setField(membershipService, "organizationRepo", repository);
        ReflectionTestUtils.setField(membershipService, "userRepo", people);
        ReflectionTestUtils.setField(membershipService, "organizationMemberRepo", members);
        ReflectionTestUtils.setField(membershipService, "accessRequestRepo", requests);
        ReflectionTestUtils.setField(membershipService, "auditLogRepo", audit);
        OrganizationController controller = new OrganizationController();
        MembershipController membershipController = new MembershipController();
        ReflectionTestUtils.setField(controller, "organizationService", service);
        ReflectionTestUtils.setField(membershipController, "membershipService", membershipService);
        mvc = MockMvcBuilders.standaloneSetup(controller, membershipController).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private Organization organization(Long id, String name, String key, String owner) {
        Organization org = new Organization();
        org.setId(id);
        org.setName(name);
        org.setAccessKey(key);
        org.setOwner(users.get(owner));
        org.setApproved(true);
        org.setStatus("APPROVED");
        organizations.put(id, org);
        return org;
    }

    private void member(String identity, OrganizationRole role) {
        OrganizationMember member = new OrganizationMember();
        member.setUser(users.get(identity + "@example.com"));
        member.setOrganization(organizations.get(1L));
        member.setRole(role);
        memberships.put(member.getUser().getId() + ":1", member);
    }

    private static String folded(String value) {
        return ValidationPatterns.normalizeOrganizationName(value).toLowerCase(Locale.ROOT);
    }

    static Stream<Arguments> normalizationCases() {
        return Stream.of(
                Arguments.of("  Conselho   Geral  ", "Conselho Geral", " #MiNerVa ", "#MiNerVa"),
                Arguments.of("Equipe\tPesquisa\r\n", "Equipe Pesquisa", "\t#RH\r\n", "#RH"),
                Arguments.of("\u00a0Área\u2009\u2009Comum\u3000", "Área Comum", "\u00a0#rh\u3000", "#rh"),
                Arguments.of("\u0085RH\ufeff", "RH", "\ufeff#rH\u0085", "#rH"),
                Arguments.of(" a ", "a", "#_-", "#_-"),
                Arguments.of("  " + "a".repeat(255) + "  ", "a".repeat(255), "#" + "a".repeat(50), "#" + "a".repeat(50))
        );
    }

    @ParameterizedTest
    @MethodSource("normalizationCases")
    void normalizationRunsBeforeValidationAndPreservesCase(String name, String expectedName, String key, String expectedKey) throws Exception {
        mvc.perform(post("/api/organizations").principal(principal("owner"))
                .contentType("application/json").content(mapper.writeValueAsString(Map.of("name", name, "accessKey", key))))
                .andExpect(status().isCreated());
        Organization saved = organizations.get(3L);
        assertEquals(expectedName, saved.getName());
        assertEquals(expectedKey, saved.getAccessKey());
    }

    static Stream<Arguments> invalidCases() {
        return Stream.of(
                Arguments.of("", "#Valid", null), Arguments.of(" \u00a0\u3000", "#Valid", null),
                Arguments.of("a".repeat(256), "#Valid", null), Arguments.of("Valid", "", null),
                Arguments.of("Valid", "#a", null), Arguments.of("Valid", "#" + "a".repeat(51), null),
                Arguments.of("Valid", "#r h", null), Arguments.of("Valid", "#r\tH", null),
                Arguments.of("Valid", "#ré", null), Arguments.of("Valid", "#Valid", "a".repeat(256))
        );
    }

    @ParameterizedTest
    @MethodSource("invalidCases")
    void invalidInputNeverReachesPersistence(String name, String key, String cnpj) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("accessKey", key);
        body.put("cnpj", cnpj);
        mvc.perform(post("/api/organizations").principal(principal("owner"))
                .contentType("application/json").content(mapper.writeValueAsString(body))).andExpect(status().isBadRequest());
        verifyNoInteractions(repository, members, audit);
    }

    @Test
    void missingMandatoryFieldsAreRejected() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertEquals(Set.of("name", "accessKey"), factory.getValidator().validate(new OrganizationRequestDTO()).stream()
                    .map(violation -> violation.getPropertyPath().toString()).collect(java.util.stream.Collectors.toSet()));
        }
    }

    @ParameterizedTest
    @CsvSource({"false, APOIO SOCIAL", "false, '  apoio   social  '", "true, APOIO SOCIAL", "true, '  apoio   social  '"})
    void nameConflictsAreGlobalOnCreationAndEdition(boolean update, String name) throws Exception {
        var request = update ? put("/api/organizations/1") : post("/api/organizations");
        String response = mvc.perform(request.principal(principal("owner")).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("name", name, "accessKey", "#Different"))))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(response.contains("nome"));
        assertEquals(2, organizations.size());
        assertEquals("Equipe Original", organizations.get(1L).getName());
        verify(repository, never()).save(any());
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @CsvSource({"false, '#BETA'", "false, '  #bEtA  '", "true, '#BETA'", "true, '  #bEtA  '"})
    void keyConflictsIgnoreCaseOnCreationAndEdition(boolean update, String key) throws Exception {
        var request = update ? put("/api/organizations/1") : post("/api/organizations");
        String response = mvc.perform(request.principal(principal("owner")).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("name", "Different", "accessKey", key))))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(response.contains("chave"));
        assertEquals("#Alpha", organizations.get(1L).getAccessKey());
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner", "vice", "sysadmin"})
    void authorizedEditingExcludesTheCurrentOrganizationAndPreservesProtectedFields(String actor) throws Exception {
        mvc.perform(put("/api/organizations/1").principal(principal(actor)).contentType("application/json")
                .content("{\"name\":\"  EQUIPE   Original  \",\"accessKey\":\" #aLPHA \",\"approved\":false,\"status\":\"PENDING\",\"owner_id\":99}"))
                .andExpect(status().isOk());
        Organization org = organizations.get(1L);
        assertEquals("EQUIPE Original", org.getName());
        assertEquals("#aLPHA", org.getAccessKey());
        assertEquals("12.345.678/0001-99", org.getCnpj());
        assertTrue(org.isApproved());
        assertEquals("APPROVED", org.getStatus());
        assertEquals(users.get("owner@example.com"), org.getOwner());
    }

    @ParameterizedTest
    @ValueSource(strings = {"subowner", "member", "outsider", "admin"})
    void unauthorizedEditingIsDeniedBeforeAvailabilityQueries(String actor) throws Exception {
        mvc.perform(put("/api/organizations/1").principal(principal(actor)).contentType("application/json")
                .content("{\"name\":\"Apoio Social\",\"accessKey\":\"#Beta\",\"role\":\"SYSADMIN\",\"owner_id\":5}"))
                .andExpect(status().isForbidden());
        verify(repository, never()).existsByNormalizedName(any(), any(), any());
        verify(repository, never()).save(any());
        verifyNoInteractions(audit);
        assertEquals("Equipe Original", organizations.get(1L).getName());
    }

    @Test
    void blankCnpjAllowsMultipleCreationsAndClearsOnlyWhenExplicitlyProvided() throws Exception {
        for (String suffix : new String[]{"One", "Two"}) {
            mvc.perform(post("/api/organizations").principal(principal("owner")).contentType("application/json")
                    .content(mapper.writeValueAsString(Map.of("name", suffix, "accessKey", "#" + suffix, "cnpj", " \u00a0 "))))
                    .andExpect(status().isCreated());
        }
        assertNull(organizations.get(3L).getCnpj());
        assertNull(organizations.get(4L).getCnpj());
        mvc.perform(put("/api/organizations/1").principal(principal("owner")).contentType("application/json")
                .content("{\"name\":\"Equipe Original\",\"accessKey\":\"#Alpha\",\"cnpj\":\"   \"}"))
                .andExpect(status().isOk());
        assertNull(organizations.get(1L).getCnpj());
    }

    @Test
    void duplicateCnpjReturnsAConflictWithoutWriting() throws Exception {
        mvc.perform(post("/api/organizations").principal(principal("owner")).contentType("application/json")
                .content("{\"name\":\"Different\",\"accessKey\":\"#Different\",\"cnpj\":\" 12.345.678/0001-99 \"}"))
                .andExpect(status().isConflict());
        verify(repository, never()).save(any());
    }

    @Test
    void creationUsesTheAuthenticatedOwnerAndDoesNotAcceptApprovalFromTheBody() throws Exception {
        mvc.perform(post("/api/organizations").principal(principal("owner")).contentType("application/json")
                .content("{\"name\":\"Different\",\"accessKey\":\"#Different\",\"owner_id\":5,\"approved\":true,\"status\":\"APPROVED\"}"))
                .andExpect(status().isCreated());
        Organization org = organizations.get(3L);
        assertEquals(users.get("owner@example.com"), org.getOwner());
        assertFalse(org.isApproved());
        assertEquals("PENDING", org.getStatus());
    }

    @Test
    void legacyDuplicatesArePreservedWhileEditingRejectsAConflictingName() throws Exception {
        organization(3L, "  equipe   ORIGINAL  ", "#Legacy", "outsider@example.com");
        mvc.perform(put("/api/organizations/1").principal(principal("owner")).contentType("application/json")
                .content("{\"name\":\"Equipe Original\",\"accessKey\":\"#Alpha\"}"))
                .andExpect(status().isConflict());
        assertEquals("  equipe   ORIGINAL  ", organizations.get(3L).getName());
        assertEquals(3, organizations.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"#alpha", " #ALPHA ", "\u00a0#aLpHa\u3000"})
    void caseInsensitiveJoinCreatesOnlyAPendingRequestForTheAuthenticatedUser(String key) throws Exception {
        String result = mvc.perform(post("/api/access-requests").principal(principal("outsider"))
                .contentType("application/json").content(mapper.writeValueAsString(Map.of("accessKey", key, "userId", 1, "role", "ORG_OWNER"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertTrue(result.contains("PENDING"));
        verify(requests).save(argThat(request -> request.getUser().equals(users.get("outsider@example.com")) && request.getOrganization().getId().equals(1L)));
        verify(members, never()).save(any());
    }

    @Test
    void ambiguousLegacyKeysNeverChooseAnOrganizationAutomatically() throws Exception {
        organization(3L, "Legacy", "#ALPHA", "outsider@example.com");
        mvc.perform(post("/api/access-requests").principal(principal("outsider"))
                .contentType("application/json").content("{\"accessKey\":\"#alpha\"}"))
                .andExpect(status().isConflict());
        verify(requests, never()).save(any());
        verify(members, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "BLOCKED", "REJECTED"})
    void approvalRestrictionsRemainInForceWithCaseInsensitiveLookup(String state) throws Exception {
        organizations.get(1L).setApproved(false);
        organizations.get(1L).setStatus(state);
        mvc.perform(post("/api/access-requests").principal(principal("outsider"))
                .contentType("application/json").content("{\"accessKey\":\"#ALPHA\"}"))
                .andExpect(status().isConflict());
        verify(requests, never()).save(any());
    }

    @Test
    void blankJoinKeyIsRejectedAndProtectedRoutesRequireAuthentication() throws Exception {
        mvc.perform(post("/api/access-requests").principal(principal("outsider"))
                .contentType("application/json").content(mapper.writeValueAsString(Map.of("accessKey", " \u00a0 "))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/organizations").contentType("application/json").content("{\"name\":\"Different\",\"accessKey\":\"#Different\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository, audit);
    }

    private UsernamePasswordAuthenticationToken principal(String identity) {
        return new UsernamePasswordAuthenticationToken(identity + "@example.com", null);
    }
}
