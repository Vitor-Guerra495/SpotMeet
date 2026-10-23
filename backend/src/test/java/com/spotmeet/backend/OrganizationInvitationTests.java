package com.spotmeet.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotmeet.backend.controller.OrganizationInvitationController;
import com.spotmeet.backend.dto.OrganizationInvitationRequestDTO;
import com.spotmeet.backend.exception.GlobalExceptionHandler;
import com.spotmeet.backend.model.*;
import com.spotmeet.backend.model.AccessRequest.AccessRequestStatus;
import com.spotmeet.backend.model.OrganizationInvitation.InvitationStatus;
import com.spotmeet.backend.model.OrganizationMember.OrganizationRole;
import com.spotmeet.backend.repository.*;
import com.spotmeet.backend.service.AdminService;
import com.spotmeet.backend.service.MembershipService;
import com.spotmeet.backend.service.OrganizationInvitationService;
import com.spotmeet.backend.service.OrganizationService;
import com.spotmeet.backend.dto.CriticalActionRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Real invitation service and controller; PostgreSQL verifies persistence and concurrency separately.
class OrganizationInvitationTests {
    private final Map<String, User> users = new HashMap<>();
    private final Map<Long, OrganizationInvitation> invitations = new LinkedHashMap<>();
    private final Map<Long, OrganizationMember> members = new HashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private Organization organization;
    private OrganizationInvitationRepository invitationRepo;
    private OrganizationRepository organizationRepo;
    private OrganizationMemberRepository memberRepo;
    private UserRepository userRepo;
    private AccessRequestRepository requestRepo;
    private AuditLogRepository auditRepo;
    private OrganizationInvitationService service;
    private MockMvc mvc;

    @BeforeEach void setUp() {
        users.clear(); invitations.clear(); members.clear();
        for (String name : new String[]{"owner", "vice", "subowner", "member", "target", "other", "sysadmin", "admin"}) {
            User user = new User();
            user.setId((long) users.size() + 1);
            user.setName("Test " + name);
            user.setEmail(name + "@example.com");
            user.setRole(name.equals("sysadmin") ? "SYSADMIN" : name.equals("admin") ? "ADMIN" : "USER");
            user.setEmailVerified(true);
            users.put(name, user);
        }
        organization = new Organization();
        organization.setId(1L);
        organization.setName("QA Organization");
        organization.setAccessKey("#QaSecret");
        organization.setOwner(users.get("owner"));
        organization.setApproved(true);
        organization.setStatus("APPROVED");
        addMember("owner", OrganizationRole.ORG_OWNER);
        addMember("vice", OrganizationRole.ORG_VICE_OWNER);
        addMember("subowner", OrganizationRole.ORG_SUBOWNER);
        addMember("member", OrganizationRole.MEMBER);
        invitationRepo = mock(OrganizationInvitationRepository.class);
        organizationRepo = mock(OrganizationRepository.class);
        memberRepo = mock(OrganizationMemberRepository.class);
        userRepo = mock(UserRepository.class);
        requestRepo = mock(AccessRequestRepository.class);
        auditRepo = mock(AuditLogRepository.class);
        when(userRepo.findByEmail(anyString())).thenAnswer(call -> users.values().stream()
                .filter(user -> user.getEmail().equals(call.getArgument(0))).findFirst());
        when(organizationRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(organization));
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(organization));
        when(memberRepo.findByUserIdAndOrganizationId(anyLong(), eq(1L)))
                .thenAnswer(call -> Optional.ofNullable(members.get(call.getArgument(0))));
        when(memberRepo.existsByUserIdAndOrganizationId(anyLong(), eq(1L)))
                .thenAnswer(call -> members.containsKey(call.getArgument(0)));
        when(memberRepo.save(any())).thenAnswer(call -> {
            OrganizationMember member = call.getArgument(0);
            members.put(member.getUser().getId(), member);
            return member;
        });
        when(invitationRepo.findByOrganizationIdAndUserId(eq(1L), anyLong())).thenAnswer(call -> invitations.values().stream()
                .filter(invitation -> invitation.getUser().getId().equals(call.getArgument(1))).findFirst());
        when(invitationRepo.save(any())).thenAnswer(call -> {
            OrganizationInvitation invitation = call.getArgument(0);
            if (invitation.getId() == null) invitation.setId((long) invitations.size() + 1);
            invitations.put(invitation.getId(), invitation);
            return invitation;
        });
        when(invitationRepo.findById(anyLong())).thenAnswer(call -> Optional.ofNullable(invitations.get(call.getArgument(0))));
        when(invitationRepo.findOrganizationIdForRecipient(anyLong(), anyLong())).thenAnswer(call -> {
            OrganizationInvitation invitation = invitations.get(call.getArgument(0));
            return invitation != null && invitation.getUser().getId().equals(call.getArgument(1))
                    ? Optional.of(1L) : Optional.empty();
        });
        service = new OrganizationInvitationService();
        ReflectionTestUtils.setField(service, "invitationRepo", invitationRepo);
        ReflectionTestUtils.setField(service, "organizationRepo", organizationRepo);
        ReflectionTestUtils.setField(service, "memberRepo", memberRepo);
        ReflectionTestUtils.setField(service, "userRepo", userRepo);
        ReflectionTestUtils.setField(service, "accessRequestRepo", requestRepo);
        ReflectionTestUtils.setField(service, "auditLogRepo", auditRepo);
        OrganizationInvitationController controller = new OrganizationInvitationController();
        ReflectionTestUtils.setField(controller, "invitationService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private void addMember(String name, OrganizationRole role) {
        OrganizationMember member = new OrganizationMember();
        member.setUser(users.get(name)); member.setOrganization(organization); member.setRole(role);
        members.put(member.getUser().getId(), member);
    }
    private UsernamePasswordAuthenticationToken identity(String name) {
        return new UsernamePasswordAuthenticationToken(users.get(name).getEmail(), "unused", List.of());
    }
    private MvcResult send(String sender, String body, int expectedStatus) throws Exception {
        return mvc.perform(post("/api/organizations/1/invitations").principal(identity(sender))
                .contentType("application/json").content(body)).andExpect(status().is(expectedStatus)).andReturn();
    }
    private OrganizationInvitation pending() {
        OrganizationInvitationRequestDTO dto = new OrganizationInvitationRequestDTO();
        dto.setEmail("target@example.com");
        Long id = service.invite(1L, dto, users.get("owner").getEmail()).getId();
        return invitations.get(id);
    }
    private MvcResult respond(String actor, Long id, String body, int expectedStatus) throws Exception {
        return mvc.perform(post("/api/organization-invitations/{id}/respond", id).principal(identity(actor))
                .contentType("application/json").content(body)).andExpect(status().is(expectedStatus)).andReturn();
    }

    @ParameterizedTest @ValueSource(strings = {"owner", "vice"})
    void onlyOwnerAndViceCanSendAndInvitationExpiresInExactly24Hours(String sender) throws Exception {
        send(sender, "{\"email\":\" TARGET@EXAMPLE.COM \"}", 201);
        OrganizationInvitation invitation = invitations.get(1L);
        assertEquals(users.get(sender), invitation.getInvitedBy());
        assertEquals(InvitationStatus.PENDING, invitation.getStatus());
        assertEquals(24, ChronoUnit.HOURS.between(invitation.getCreatedAt(), invitation.getExpiresAt()));
        assertEquals(invitation.getCreatedAt().plus(24, ChronoUnit.HOURS), invitation.getExpiresAt());
        assertNull(invitation.getRespondedAt());
        verify(memberRepo, never()).save(any());
    }
    @ParameterizedTest @ValueSource(strings = {"subowner", "member", "other", "sysadmin", "admin"})
    void otherRolesCannotInviteOrLookUpTheTargetAccount(String sender) throws Exception {
        send(sender, "{\"email\":\"absent@example.com\"}", 403);
        verify(userRepo, never()).findByEmail("absent@example.com");
        verify(invitationRepo, never()).save(any());
    }
    @Test void leaderOfAnotherOrganizationCannotInviteHere() throws Exception {
        Organization other = new Organization(); other.setId(2L); other.setOwner(users.get("other"));
        when(organizationRepo.findByIdForUpdate(2L)).thenReturn(Optional.of(other));
        send("other", "{\"email\":\"target@example.com\"}", 403);
    }
    @ParameterizedTest @ValueSource(strings = {"teste@gmail", "a@dom_x.com", "ana@gmail.c", "a@example.com' OR 1=1", "<script>@example.com", ""})
    void invalidEmailsAreRejectedBeforeTheService(String email) throws Exception {
        send("owner", mapper.writeValueAsString(Map.of("email", email)), 400);
        verifyNoInteractions(organizationRepo, invitationRepo);
    }
    @Test void missingEmailIsRejected() throws Exception { send("owner", "{}", 400); }
    @Test void unregisteredTargetIsNotInvited() throws Exception {
        send("owner", "{\"email\":\"absent@example.com\"}", 404);
        verify(invitationRepo, never()).save(any());
    }
    @Test void unverifiedTargetIsNotInvited() throws Exception {
        users.get("target").setEmailVerified(false);
        send("owner", "{\"email\":\"target@example.com\"}", 404);
    }
    @ParameterizedTest @ValueSource(strings = {"owner", "member"})
    void alreadyBelongingToOrganizationCannotBeInvited(String target) throws Exception {
        send("owner", mapper.writeValueAsString(Map.of("email", users.get(target).getEmail())), 409);
    }
    @ParameterizedTest @ValueSource(strings = {"PENDING", "BLOCKED", "REJECTED"})
    void unapprovedOrganizationCannotSendInvitations(String status) throws Exception {
        organization.setApproved(false); organization.setStatus(status);
        send("owner", "{\"email\":\"target@example.com\"}", 409);
    }
    @Test void duplicatePendingInvitationDoesNotRefreshItsDeadline() throws Exception {
        OrganizationInvitation invitation = pending(); Instant expires = invitation.getExpiresAt();
        send("vice", "{\"email\":\"target@example.com\"}", 409);
        assertEquals(expires, invitation.getExpiresAt()); assertEquals(users.get("owner"), invitation.getInvitedBy());
    }
    @ParameterizedTest @ValueSource(strings = {"expired", "rejected"})
    void expiredOrRejectedInvitationCanBeRenewed(String previous) throws Exception {
        OrganizationInvitation invitation = pending();
        invitation.setExpiresAt(Instant.now().minusSeconds(1));
        if (previous.equals("rejected")) {
            invitation.setStatus(InvitationStatus.REJECTED); invitation.setRespondedAt(Instant.now());
        }
        send("vice", "{\"email\":\"target@example.com\"}", 201);
        assertEquals(1, invitations.size()); assertEquals(InvitationStatus.PENDING, invitation.getStatus());
        assertEquals(users.get("vice"), invitation.getInvitedBy()); assertNull(invitation.getRespondedAt());
        assertTrue(invitation.getExpiresAt().isAfter(Instant.now().plus(23, ChronoUnit.HOURS)));
    }
    @Test void listingIsAlwaysScopedToTheAuthenticatedRecipient() throws Exception {
        OrganizationInvitation invitation = pending();
        when(invitationRepo.findPendingForRecipient(eq(users.get("target").getId()), eq(InvitationStatus.PENDING), any()))
                .thenReturn(List.of(invitation));
        MvcResult response = mvc.perform(get("/api/organization-invitations/mine").principal(identity("target"))
                .param("userId", users.get("other").getId().toString())).andExpect(status().isOk()).andReturn();
        assertEquals(1, mapper.readTree(response.getResponse().getContentAsString()).size());
        verify(invitationRepo).findPendingForRecipient(eq(users.get("target").getId()), eq(InvitationStatus.PENDING), any());
    }
    @Test void responseDoesNotExposeKeysEmailsPasswordsOrTokens() throws Exception {
        MvcResult result = send("owner", "{\"email\":\"target@example.com\"}", 201);
        JsonNode node = mapper.readTree(result.getResponse().getContentAsString());
        assertEquals("QA Organization", node.get("organizationName").asText());
        for (String field : List.of("accessKey", "organizationAccessKey", "email", "userEmail", "password", "token", "role")) {
            assertFalse(node.has(field));
        }
        assertFalse(result.getResponse().getContentAsString().contains("#QaSecret"));
    }
    @Test void acceptanceCreatesOnlyRegularMembershipAndConsumesInvitation() throws Exception {
        OrganizationInvitation invitation = pending();
        respond("target", invitation.getId(), "{\"accept\":true,\"role\":\"ORG_OWNER\",\"userId\":1}", 200);
        assertEquals(OrganizationRole.MEMBER, members.get(users.get("target").getId()).getRole());
        assertEquals(InvitationStatus.ACCEPTED, invitation.getStatus()); assertNotNull(invitation.getRespondedAt());
        respond("target", invitation.getId(), "{\"accept\":true}", 409);
        verify(memberRepo, times(1)).save(any());
    }
    @Test void declineDoesNotCreateMembershipAndCannotBeReplayed() throws Exception {
        OrganizationInvitation invitation = pending();
        respond("target", invitation.getId(), "{\"accept\":false}", 200);
        assertEquals(InvitationStatus.REJECTED, invitation.getStatus());
        verify(memberRepo, never()).save(any());
        respond("target", invitation.getId(), "{\"accept\":true}", 409);
    }
    @ParameterizedTest @ValueSource(strings = {"other", "owner", "sysadmin"})
    void noOneCanAnswerForAnotherRecipient(String actor) throws Exception {
        OrganizationInvitation invitation = pending();
        respond(actor, invitation.getId(), "{\"accept\":true,\"userId\":5}", 403);
        assertEquals(InvitationStatus.PENDING, invitation.getStatus()); verify(memberRepo, never()).save(any());
    }
    @Test void unknownInvitationDoesNotDiscloseItsExistenceToOtherUsers() throws Exception {
        respond("other", 987L, "{\"accept\":false}", 403);
    }
    @Test void expiredInvitationCannotBeAcceptedOrDeclined() throws Exception {
        OrganizationInvitation invitation = pending(); invitation.setExpiresAt(Instant.now());
        respond("target", invitation.getId(), "{\"accept\":true}", 409);
        respond("target", invitation.getId(), "{\"accept\":false}", 409);
        assertEquals(InvitationStatus.PENDING, invitation.getStatus()); verify(memberRepo, never()).save(any());
    }
    @Test void organizationBlockedAfterSendingCannotGainMembers() throws Exception {
        OrganizationInvitation invitation = pending(); organization.setApproved(false);
        respond("target", invitation.getId(), "{\"accept\":true}", 409);
        verify(memberRepo, never()).save(any());
        respond("target", invitation.getId(), "{\"accept\":false}", 200);
    }
    @Test void joiningByAnotherPathDoesNotAllowInvitationToOverwriteTheRole() throws Exception {
        OrganizationInvitation invitation = pending(); addMember("target", OrganizationRole.ORG_VICE_OWNER);
        respond("target", invitation.getId(), "{\"accept\":true}", 409);
        assertEquals(OrganizationRole.ORG_VICE_OWNER, members.get(users.get("target").getId()).getRole());
    }
    @Test void acceptanceApprovesPendingKeyRequestButKeepsPriorRejections() throws Exception {
        OrganizationInvitation invitation = pending();
        AccessRequest pending = new AccessRequest(); pending.setStatus(AccessRequestStatus.PENDING);
        AccessRequest rejected = new AccessRequest(); rejected.setStatus(AccessRequestStatus.REJECTED);
        when(requestRepo.findByUserIdAndOrganizationId(users.get("target").getId(), 1L)).thenReturn(List.of(pending, rejected));
        respond("target", invitation.getId(), "{\"accept\":true}", 200);
        assertEquals(AccessRequestStatus.APPROVED, pending.getStatus()); assertEquals(AccessRequestStatus.REJECTED, rejected.getStatus());
        verify(requestRepo).save(pending); verify(requestRepo, never()).save(rejected);
    }
    @Test void changedEmailKeepsInvitationAttachedToTheSameAccountId() throws Exception {
        OrganizationInvitation invitation = pending(); users.get("target").setEmail("updated@example.com");
        respond("target", invitation.getId(), "{\"accept\":true}", 200);
    }
    @Test void bodyCannotChooseInviterStatusOrganizationOrExpiry() throws Exception {
        send("vice", "{\"email\":\"target@example.com\",\"invitedById\":1,\"organizationId\":2,\"status\":\"ACCEPTED\",\"expiresAt\":\"2099-01-01T00:00:00Z\"}", 201);
        OrganizationInvitation invitation = invitations.get(1L);
        assertEquals(users.get("vice"), invitation.getInvitedBy()); assertEquals(organization, invitation.getOrganization());
        assertEquals(InvitationStatus.PENDING, invitation.getStatus());
        assertTrue(invitation.getExpiresAt().isBefore(Instant.now().plus(25, ChronoUnit.HOURS)));
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"accept\":null}"})
    void responseMustExpressConsent(String body) throws Exception {
        respond("target", pending().getId(), body, 400); verify(memberRepo, never()).save(any());
    }
    @ParameterizedTest @ValueSource(strings = {"{", "{\"accept\":\"invalid\"}", "{\"accept\":{}}"})
    void malformedResponseBodiesReturn400WithoutBusinessSideEffects(String body) throws Exception {
        OrganizationInvitation invitation = pending();
        respond("target", invitation.getId(), body, 400);
        verify(memberRepo, never()).save(any());
    }
    @Test void nonNumericInvitationIdReturns400() throws Exception {
        mvc.perform(post("/api/organization-invitations/invalid/respond").principal(identity("target"))
                .contentType("application/json").content("{\"accept\":true}")).andExpect(status().isBadRequest());
        verifyNoInteractions(invitationRepo);
    }
    @Test void unauthenticatedRoutesReturn401() throws Exception {
        mvc.perform(get("/api/organization-invitations/mine")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/organizations/1/invitations").contentType("application/json")
                .content("{\"email\":\"target@example.com\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/organization-invitations/1/respond").contentType("application/json")
                .content("{\"accept\":true}")).andExpect(status().isUnauthorized());
    }
    @Test void databaseErrorsDoNotLeakSqlOrCredentials() throws Exception {
        doThrow(new DataIntegrityViolationException("private database detail")).when(invitationRepo).save(any());
        MvcResult result = send("owner", "{\"email\":\"target@example.com\"}", 500);
        assertFalse(result.getResponse().getContentAsString().contains("private database detail"));
    }
    @Test void deletionClearsInvitationsBeforeTheOrganization() {
        OrganizationService organizationService = new OrganizationService();
        CommitteeRepository committees = mock(CommitteeRepository.class);
        ReflectionTestUtils.setField(organizationService, "userRepo", userRepo);
        ReflectionTestUtils.setField(organizationService, "organizationRepo", organizationRepo);
        ReflectionTestUtils.setField(organizationService, "organizationMemberRepo", memberRepo);
        ReflectionTestUtils.setField(organizationService, "committeeRepo", committees);
        ReflectionTestUtils.setField(organizationService, "committeeMemberRepo", mock(CommitteeMemberRepository.class));
        ReflectionTestUtils.setField(organizationService, "accessRequestRepo", requestRepo);
        ReflectionTestUtils.setField(organizationService, "invitationRepo", invitationRepo);
        ReflectionTestUtils.setField(organizationService, "auditLogRepo", auditRepo);
        organizationService.deleteOrganization(1L, users.get("owner").getEmail());
        var order = inOrder(invitationRepo, organizationRepo);
        order.verify(organizationRepo).findByIdForUpdate(1L);
        order.verify(invitationRepo).deleteByOrganizationId(1L);
        order.verify(organizationRepo).delete(organization);
    }
    @Test void resetClearsInvitationsBeforeOrganizationsAndUsers() {
        AdminService admin = new AdminService();
        User executor = users.get("sysadmin");
        when(userRepo.findByEmailForUpdate(executor.getEmail())).thenReturn(Optional.of(executor));
        ReflectionTestUtils.setField(admin, "userRepo", userRepo);
        ReflectionTestUtils.setField(admin, "organizationRepo", organizationRepo);
        ReflectionTestUtils.setField(admin, "organizationMemberRepo", memberRepo);
        ReflectionTestUtils.setField(admin, "committeeRepo", mock(CommitteeRepository.class));
        ReflectionTestUtils.setField(admin, "committeeMemberRepo", mock(CommitteeMemberRepository.class));
        ReflectionTestUtils.setField(admin, "accessRequestRepo", requestRepo);
        ReflectionTestUtils.setField(admin, "invitationRepo", invitationRepo);
        ReflectionTestUtils.setField(admin, "auditLogRepo", auditRepo);
        CriticalActionRequestDTO dto = new CriticalActionRequestDTO(); dto.setConfirmation("CONFIRM_FULL_RESET");
        admin.resetSystem(dto, executor.getEmail());
        var order = inOrder(invitationRepo, organizationRepo, userRepo);
        order.verify(invitationRepo).deleteAllInBatch();
        order.verify(organizationRepo).deleteAllInBatch();
        order.verify(userRepo).deleteAllExceptAdmin(executor.getId());
        assertNotNull(executor.getTokensValidAfter());
    }
    @Test void removingMemberAlsoRemovesInvitationsThatCouldRestoreAccess() {
        pending(); addMember("target", OrganizationRole.MEMBER);
        MembershipService membership = new MembershipService();
        ReflectionTestUtils.setField(membership, "userRepo", userRepo);
        ReflectionTestUtils.setField(membership, "organizationRepo", organizationRepo);
        ReflectionTestUtils.setField(membership, "organizationMemberRepo", memberRepo);
        ReflectionTestUtils.setField(membership, "accessRequestRepo", requestRepo);
        ReflectionTestUtils.setField(membership, "invitationRepo", invitationRepo);
        ReflectionTestUtils.setField(membership, "committeeMemberRepo", mock(CommitteeMemberRepository.class));
        ReflectionTestUtils.setField(membership, "auditLogRepo", auditRepo);
        OrganizationMember target = members.get(users.get("target").getId());
        membership.revokeAccess(target.getUser().getId(), 1L, users.get("owner").getEmail());
        var order = inOrder(invitationRepo, memberRepo);
        order.verify(invitationRepo).deleteByOrganizationIdAndUserId(1L, target.getUser().getId());
        order.verify(memberRepo).delete(target);
    }
    private MvcResult candidates(String sender, String query, int expectedStatus) throws Exception {
        return mvc.perform(get("/api/organizations/1/invitations/candidates").param("q", query).principal(identity(sender)))
                .andExpect(status().is(expectedStatus)).andReturn();
    }
    @Test void candidateSearchNeedsThreeCharactersAndReturnsOnlyNameAndEmail() throws Exception {
        User found = users.get("target");
        when(userRepo.findInviteCandidates(eq("tar"), eq(1L), eq(1L), any(Instant.class), eq(org.springframework.data.domain.PageRequest.of(0, 10))))
                .thenReturn(List.of(found));
        candidates("owner", " ta ", 200);
        verify(userRepo, never()).findInviteCandidates(anyString(), anyLong(), anyLong(), any(), any());
        JsonNode rows = mapper.readTree(candidates("vice", " tar ", 200).getResponse().getContentAsString());
        assertEquals(1, rows.size());
        assertEquals(Set.of("name", "email"), new HashSet<>(rows.get(0).properties().stream().map(Map.Entry::getKey).toList()));
        assertEquals("target@example.com", rows.get(0).get("email").asText());
    }
    @Test void candidateSearchEscapesLikeWildcards() throws Exception {
        candidates("owner", "50%_a!", 200);
        verify(userRepo).findInviteCandidates(eq("50!%!_a!!"), eq(1L), eq(1L), any(Instant.class), any());
    }
    @ParameterizedTest @ValueSource(strings = {"subowner", "member", "other", "sysadmin", "admin"})
    void otherRolesCannotSearchForPeopleToInvite(String sender) throws Exception {
        candidates(sender, "target", 403);
        verify(userRepo, never()).findInviteCandidates(anyString(), anyLong(), anyLong(), any(), any());
    }
    @Test void candidateSearchRequiresAnApprovedOrganization() throws Exception {
        organization.setApproved(false); organization.setStatus("PENDING");
        candidates("owner", "target", 409);
        verify(userRepo, never()).findInviteCandidates(anyString(), anyLong(), anyLong(), any(), any());
    }
    private MembershipService membershipForPreview() {
        MembershipService membership = new MembershipService();
        ReflectionTestUtils.setField(membership, "organizationRepo", organizationRepo);
        when(organizationRepo.findIdsByAccessKeyIgnoreCase(anyString(), anyString())).thenReturn(List.of(1L));
        return membership;
    }
    @Test void previewShowsOnlyNameKeyAndResponsibleOfAnApprovedOrganization() {
        var preview = membershipForPreview().previewByKey(" #qasecret ");
        assertEquals("QA Organization", preview.name());
        assertEquals("#QaSecret", preview.accessKey());
        assertEquals("Test owner", preview.ownerName());
        assertEquals(3, preview.getClass().getRecordComponents().length);
    }
    @Test void previewRefusesUnknownEmptyAndUnapprovedKeys() {
        MembershipService membership = membershipForPreview();
        assertThrows(IllegalArgumentException.class, () -> membership.previewByKey("  "));
        assertThrows(IllegalArgumentException.class, () -> membership.previewByKey("#OtherKey"));
        organization.setApproved(false); organization.setStatus("BLOCKED");
        assertThrows(IllegalStateException.class, () -> membership.previewByKey("#QaSecret"));
        when(organizationRepo.findIdsByAccessKeyIgnoreCase(anyString(), anyString())).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> membership.previewByKey("#Missing"));
    }
    @Test void keyApprovalUsesTheSameOrganizationLockAndKeepsItsPermissions() {
        MembershipService membership = new MembershipService();
        ReflectionTestUtils.setField(membership, "userRepo", userRepo);
        ReflectionTestUtils.setField(membership, "organizationRepo", organizationRepo);
        ReflectionTestUtils.setField(membership, "organizationMemberRepo", memberRepo);
        ReflectionTestUtils.setField(membership, "accessRequestRepo", requestRepo);
        ReflectionTestUtils.setField(membership, "auditLogRepo", auditRepo);
        AccessRequest request = new AccessRequest(); request.setId(3L); request.setOrganization(organization);
        request.setUser(users.get("target")); request.setStatus(AccessRequestStatus.PENDING);
        when(requestRepo.findOrganizationIdById(3L)).thenReturn(Optional.of(1L));
        when(requestRepo.findById(3L)).thenReturn(Optional.of(request));
        assertThrows(SecurityException.class, () -> membership.approveAccess(3L, users.get("other").getEmail()));
        verify(memberRepo, never()).save(any());
        membership.approveAccess(3L, users.get("vice").getEmail());
        assertEquals(AccessRequestStatus.APPROVED, request.getStatus());
        var order = inOrder(organizationRepo, requestRepo);
        order.verify(requestRepo).findOrganizationIdById(3L);
        order.verify(organizationRepo).findByIdForUpdate(1L);
        order.verify(requestRepo).findById(3L);
    }
}
