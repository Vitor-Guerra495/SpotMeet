package com.spotmeet.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotmeet.backend.controller.OrganizationLeadershipController;
import com.spotmeet.backend.exception.GlobalExceptionHandler;
import com.spotmeet.backend.model.*;
import com.spotmeet.backend.model.OrganizationMember.OrganizationRole;
import com.spotmeet.backend.repository.*;
import com.spotmeet.backend.service.MembershipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Transaction rollback and competing requests are verified separately on PostgreSQL.
class OrganizationLeadershipTests {
    private final Map<String, User> people = new LinkedHashMap<>();
    private final Map<Long, OrganizationMember> members = new LinkedHashMap<>();
    private Organization organization;
    private OrganizationRepository organizations;
    private OrganizationMemberRepository memberships;
    private UserRepository users;
    private AuditLogRepository audit;
    private MembershipService service;
    private MockMvc mvc;

    @BeforeEach void setUp() {
        for (String name : List.of("owner", "member", "vice", "subowner", "outsider", "sysadmin", "admin")) {
            User user = new User(); user.setId((long) people.size() + 1); user.setName("QA " + name);
            user.setEmail(name + "@example.com"); user.setEmailVerified(true);
            user.setRole(name.equals("sysadmin") ? "SYSADMIN" : name.equals("admin") ? "ADMIN" : "USER");
            people.put(name, user);
        }
        organization = new Organization(); organization.setId(1L); organization.setName("Equipe QA");
        organization.setAccessKey("#QA27"); organization.setCnpj("QA-private");
        organization.setOwner(people.get("owner")); organization.setApproved(true); organization.setStatus("APPROVED");
        membership("owner", OrganizationRole.ORG_OWNER);
        membership("member", OrganizationRole.MEMBER);
        membership("vice", OrganizationRole.ORG_VICE_OWNER);
        membership("subowner", OrganizationRole.ORG_SUBOWNER);
        users = mock(UserRepository.class); organizations = mock(OrganizationRepository.class);
        memberships = mock(OrganizationMemberRepository.class); audit = mock(AuditLogRepository.class);
        when(users.findByEmail(anyString())).thenAnswer(call -> people.values().stream()
                .filter(user -> user.getEmail().equals(call.getArgument(0))).findFirst());
        when(users.findById(anyLong())).thenAnswer(call -> people.values().stream()
                .filter(user -> user.getId().equals(call.getArgument(0))).findFirst());
        when(organizations.findByIdForUpdate(anyLong())).thenAnswer(call ->
                organization.getId().equals(call.getArgument(0)) ? Optional.of(organization) : Optional.empty());
        when(organizations.findById(anyLong())).thenAnswer(call ->
                organization.getId().equals(call.getArgument(0)) ? Optional.of(organization) : Optional.empty());
        when(memberships.findByUserIdAndOrganizationId(anyLong(), anyLong())).thenAnswer(call ->
                organization.getId().equals(call.getArgument(1)) ? Optional.ofNullable(members.get(call.getArgument(0))) : Optional.empty());
        when(memberships.findByOrganizationIdAndRole(anyLong(), eq(OrganizationRole.ORG_OWNER))).thenAnswer(call ->
                members.values().stream().filter(member -> member.getRole() == OrganizationRole.ORG_OWNER).toList());
        service = new MembershipService();
        ReflectionTestUtils.setField(service, "userRepo", users);
        ReflectionTestUtils.setField(service, "organizationRepo", organizations);
        ReflectionTestUtils.setField(service, "organizationMemberRepo", memberships);
        ReflectionTestUtils.setField(service, "auditLogRepo", audit);
        OrganizationLeadershipController controller = new OrganizationLeadershipController();
        ReflectionTestUtils.setField(controller, "membershipService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    private void membership(String name, OrganizationRole role) {
        OrganizationMember member = new OrganizationMember(); member.setId(people.get(name).getId());
        member.setUser(people.get(name)); member.setOrganization(organization); member.setRole(role);
        members.put(member.getUser().getId(), member);
    }
    private UsernamePasswordAuthenticationToken identity(String name) {
        return new UsernamePasswordAuthenticationToken(people.get(name).getEmail(), "unused", List.of());
    }
    private String path(String target) { return "/api/organizations/1/members/" + people.get(target).getId() + "/make-owner"; }
    private void unchanged() {
        assertEquals(people.get("owner"), organization.getOwner());
        assertEquals(OrganizationRole.ORG_OWNER, members.get(people.get("owner").getId()).getRole());
        verify(organizations, never()).save(any()); verify(memberships, never()).save(any()); verifyNoInteractions(audit);
    }

    @ParameterizedTest @CsvSource({"owner,member", "owner,vice", "owner,subowner", "sysadmin,member", "sysadmin,vice", "sysadmin,subowner"})
    void ownerOrSysadminCanTransferToAnyCurrentMember(String actor, String target) throws Exception {
        var response = mvc.perform(post(path(target)).principal(identity(actor))).andExpect(status().isOk()).andReturn();
        assertEquals(people.get(target), organization.getOwner());
        assertEquals(OrganizationRole.MEMBER, members.get(people.get("owner").getId()).getRole());
        assertEquals(OrganizationRole.ORG_OWNER, members.get(people.get(target).getId()).getRole());
        assertEquals(1, members.values().stream().filter(member -> member.getRole() == OrganizationRole.ORG_OWNER).count());
        assertEquals(4, members.size()); assertTrue(organization.isApproved()); assertEquals("APPROVED", organization.getStatus());
        assertEquals("Equipe QA", organization.getName()); assertEquals("#QA27", organization.getAccessKey()); assertEquals("QA-private", organization.getCnpj());
        var json = new ObjectMapper().readTree(response.getResponse().getContentAsString());
        assertEquals(1, json.get("organizationId").asLong()); assertEquals(people.get(target).getId().longValue(), json.get("ownerId").asLong());
        assertEquals("MEMBER", json.get("previousOwnerRole").asText()); assertEquals("ORG_OWNER", json.get("ownerRole").asText());
        assertEquals("USER", people.get("owner").getRole());
    }

    @ParameterizedTest @ValueSource(strings = {"member", "vice", "subowner", "outsider", "admin"})
    void otherRolesCannotTransferOrProbeTheTarget(String actor) throws Exception {
        mvc.perform(post(path("member")).principal(identity(actor)).param("role", "SYSADMIN")).andExpect(status().isForbidden());
        verify(memberships, never()).findByUserIdAndOrganizationId(any(), any());
        verify(memberships, never()).findByOrganizationIdAndRole(any(), any()); unchanged();
    }

    @Test void aStrayOwnerRoleDoesNotOverrideTheCanonicalOwnerId() throws Exception {
        members.get(people.get("vice").getId()).setRole(OrganizationRole.ORG_OWNER);
        mvc.perform(post(path("member")).principal(identity("vice"))).andExpect(status().isForbidden()); unchanged();
    }

    @Test void missingExecutorCannotTransfer() throws Exception {
        var removed = new UsernamePasswordAuthenticationToken("removed@example.com", "unused", List.of());
        mvc.perform(post(path("member")).principal(removed)).andExpect(status().isForbidden());
        verifyNoInteractions(organizations, memberships, audit);
    }

    @Test void missingAuthenticationDoesNotReadTheOrganization() throws Exception {
        mvc.perform(post(path("member"))).andExpect(status().isUnauthorized()); verifyNoInteractions(users, organizations, memberships, audit);
    }

    @Test void missingOrganizationReturns404() throws Exception {
        mvc.perform(post("/api/organizations/999/members/2/make-owner").principal(identity("owner"))).andExpect(status().isNotFound());
        unchanged();
    }

    @ParameterizedTest @ValueSource(strings = {"outsider", "sysadmin", "admin"})
    void anExistingAccountWithoutAMembershipCannotBecomeOwner(String target) throws Exception {
        mvc.perform(post(path(target)).principal(identity("owner"))).andExpect(status().isNotFound()); unchanged();
    }

    @Test void onlyTheUserIdInTheRouteDeterminesTheDestination() throws Exception {
        mvc.perform(post(path("member")).principal(identity("owner")).contentType("application/json")
                .content("{\"ownerId\":5,\"userId\":5,\"role\":\"SYSADMIN\",\"previousOwnerRole\":\"ORG_VICE_OWNER\",\"approved\":false}"))
                .andExpect(status().isOk());
        assertEquals(people.get("member"), organization.getOwner()); assertTrue(organization.isApproved());
        assertEquals(OrganizationRole.MEMBER, members.get(people.get("owner").getId()).getRole());
    }

    @Test void theOwnerCannotTransferToThemselves() throws Exception {
        mvc.perform(post(path("owner")).principal(identity("owner"))).andExpect(status().isConflict()); unchanged();
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "duplicate", "different", "null"})
    void inconsistentExistingOwnershipIsRejectedWithoutRepairingLocalData(String state) throws Exception {
        if (state.equals("missing")) members.remove(people.get("owner").getId());
        if (state.equals("duplicate")) members.get(people.get("vice").getId()).setRole(OrganizationRole.ORG_OWNER);
        if (state.equals("different")) members.get(people.get("owner").getId()).setRole(OrganizationRole.MEMBER);
        if (state.equals("null")) organization.setOwner(null);
        mvc.perform(post(path("member")).principal(identity("sysadmin"))).andExpect(status().isConflict());
        verify(organizations, never()).save(any()); verify(memberships, never()).save(any()); verifyNoInteractions(audit);
    }

    @Test void aMissingDestinationRoleIsAConflict() throws Exception {
        members.get(people.get("member").getId()).setRole(null);
        mvc.perform(post(path("member")).principal(identity("owner"))).andExpect(status().isConflict()); unchanged();
    }

    @Test void transferringKeepsTheApprovedStatus() throws Exception {
        mvc.perform(post(path("member")).principal(identity("owner"))).andExpect(status().isOk());
        assertEquals("APPROVED", organization.getStatus()); assertTrue(organization.isApproved());
    }

    // Since 22/10/2026 the leadership can only move after the system administrator approves the organization.
    @ParameterizedTest @ValueSource(strings = {"PENDING", "BLOCKED", "REJECTED"})
    void organizationsThatAreNotApprovedCannotTransferLeadership(String state) throws Exception {
        organization.setStatus(state); organization.setApproved(false);
        mvc.perform(post(path("member")).principal(identity("owner"))).andExpect(status().isConflict());
        unchanged(); assertEquals(state, organization.getStatus());
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "invalid", "1.5", "9223372036854775808", "1%27OR%271=1"})
    void invalidOrganizationIdsReturn400WithoutCollectingData(String id) throws Exception {
        mvc.perform(post("/api/organizations/" + id + "/members/2/make-owner").principal(identity("owner"))).andExpect(status().isBadRequest());
        verifyNoInteractions(users, organizations, memberships, audit);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "invalid", "1.5", "9223372036854775808"})
    void invalidMemberIdsReturn400WithoutCollectingData(String id) throws Exception {
        mvc.perform(post("/api/organizations/1/members/" + id + "/make-owner").principal(identity("owner"))).andExpect(status().isBadRequest());
        verifyNoInteractions(users, organizations, memberships, audit);
    }

    @Test void auditAndResponseOnlyIncludeTheRelevantIdsAndRoles() throws Exception {
        var response = mvc.perform(post(path("member")).principal(identity("owner"))).andExpect(status().isOk()).andReturn();
        var json = new ObjectMapper().readTree(response.getResponse().getContentAsString()); assertEquals(5, json.size());
        ArgumentCaptor<AuditLog> record = ArgumentCaptor.forClass(AuditLog.class); verify(audit).save(record.capture());
        assertEquals("ORGANIZATION_LEADER_CHANGED", record.getValue().getAction()); assertEquals(people.get("owner"), record.getValue().getUser());
        String text = response.getResponse().getContentAsString() + record.getValue().getDetails();
        for (String value : List.of("@example.com", "#QA27", "QA-private", "password", "token")) assertFalse(text.contains(value));
    }

    @Test void aFormerOwnerCannotUseTheSameIdentityToTransferAgain() throws Exception {
        mvc.perform(post(path("member")).principal(identity("owner"))).andExpect(status().isOk());
        mvc.perform(post(path("vice")).principal(identity("owner"))).andExpect(status().isForbidden());
        mvc.perform(post(path("owner")).principal(identity("member"))).andExpect(status().isOk());
        assertEquals(people.get("owner"), organization.getOwner());
        assertEquals(OrganizationRole.MEMBER, members.get(people.get("member").getId()).getRole());
    }

    @Test void formerOwnershipDoesNotRemoveAnExistingGlobalSysadminRole() throws Exception {
        people.get("owner").setRole("SYSADMIN");
        mvc.perform(post(path("member")).principal(identity("owner"))).andExpect(status().isOk());
        assertEquals(OrganizationRole.MEMBER, members.get(people.get("owner").getId()).getRole());
        mvc.perform(post(path("vice")).principal(identity("owner"))).andExpect(status().isOk());
        assertEquals("SYSADMIN", people.get("owner").getRole());
    }

    @Test void auditPersistenceErrorsDoNotExposeInternalDetails() throws Exception {
        when(audit.save(any())).thenThrow(new RuntimeException("private SQL and token"));
        var response = mvc.perform(post(path("member")).principal(identity("owner"))).andExpect(status().isInternalServerError()).andReturn();
        assertFalse(response.getResponse().getContentAsString().contains("private SQL"));
    }

    @Test void theSharedRepositoryReservesWriteAccessBeforeTakingTheRowLock() {
        OrganizationRepository repository = mock(OrganizationRepository.class, CALLS_REAL_METHODS);
        when(repository.findLockedById(1L)).thenReturn(Optional.of(organization));
        assertEquals(organization, repository.findByIdForUpdate(1L).orElseThrow());
        var order = inOrder(repository); order.verify(repository).lockForWrite(); order.verify(repository).findLockedById(1L);
    }
}
