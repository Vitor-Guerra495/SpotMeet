package com.spotmeet.backend.service;

import com.spotmeet.backend.controller.AdminController;
import com.spotmeet.backend.dto.*;
import com.spotmeet.backend.exception.GlobalExceptionHandler;
import com.spotmeet.backend.exception.InvalidModerationReasonException;
import com.spotmeet.backend.model.*;
import com.spotmeet.backend.repository.*;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrganizationModerationTests {
    private User owner, member, admin;
    private Organization org;
    private UserRepository users;
    private OrganizationRepository organizations;
    private OrganizationMemberRepository memberships;
    private AccessRequestRepository requests;
    private AdminService moderation;
    private MembershipService membership;
    private AuditLogRepository audit;
    private MockMvc mvc;

    @BeforeEach void setUp() {
        owner = user(1, "owner", "USER"); member = user(2, "member", "USER"); admin = user(3, "admin", "SYSADMIN");
        users = mock(UserRepository.class); organizations = mock(OrganizationRepository.class);
        memberships = mock(OrganizationMemberRepository.class); requests = mock(AccessRequestRepository.class);
        audit = mock(AuditLogRepository.class);
        when(users.findByEmail(anyString())).thenAnswer(call -> List.of(owner, member, admin).stream().filter(user -> user.getEmail().equals(call.getArgument(0))).findFirst());
        when(users.findByEmailForUpdate(anyString())).thenAnswer(call -> users.findByEmail(call.getArgument(0)));
        org = new Organization(); org.setId(7L); org.setName("Equipe QA"); org.setAccessKey("#EquipeQA"); org.setOwner(owner); org.setStatus("PENDING");
        when(organizations.findByIdForUpdate(7L)).thenReturn(Optional.of(org)); when(organizations.findById(7L)).thenReturn(Optional.of(org));
        when(organizations.findIdsByAccessKeyIgnoreCase(eq("#EquipeQA"), anyString())).thenReturn(List.of(org.getId()));
        when(organizations.save(any())).thenAnswer(call -> { Organization row = call.getArgument(0); if (row.getId() == null) row.setId(8L); return row; });
        when(requests.save(any())).thenAnswer(call -> { AccessRequest row = call.getArgument(0); row.setId(9L); return row; });
        moderation = new AdminService(); membership = new MembershipService();
        for (Object target : List.of(moderation, membership)) {
            ReflectionTestUtils.setField(target, "organizationRepo", organizations); ReflectionTestUtils.setField(target, "userRepo", users);
            ReflectionTestUtils.setField(target, "organizationMemberRepo", memberships); ReflectionTestUtils.setField(target, "auditLogRepo", audit);
        }
        ReflectionTestUtils.setField(moderation, "committeeRepo", mock(CommitteeRepository.class));
        ReflectionTestUtils.setField(membership, "accessRequestRepo", requests);
        AdminController controller = new AdminController(); ReflectionTestUtils.setField(controller, "adminService", moderation);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    private User user(long id, String name, String role) {
        User user = new User(); user.setId(id); user.setName(name); user.setEmail(name + "@example.com"); user.setRole(role); user.setEmailVerified(true); return user;
    }
    private UsernamePasswordAuthenticationToken principal(User user) { return new UsernamePasswordAuthenticationToken(user.getEmail(), null, user.getAuthorities()); }

    @ParameterizedTest @ValueSource(strings = {"", " ", "ab", "\t\n", "abc\u0000def", "abc\u007f", "\u00A0\u00A0\u00A0", "\uFEFF\uFEFF\uFEFF"})
    void rejectsInvalidReasonsWithoutWriting(String reason) {
        assertThrows(InvalidModerationReasonException.class, () -> moderation.rejectOrganization(7L, reason, admin.getEmail()));
        assertThrows(InvalidModerationReasonException.class, () -> moderation.updateOrganizationStatus(7L, false, reason, admin.getEmail()));
        verify(organizations, never()).save(any());
    }
    @Test void rejectsNullAndOversizedReasons() {
        for (String reason : new String[]{null, "a".repeat(501)}) {
            assertThrows(InvalidModerationReasonException.class, () -> moderation.rejectOrganization(7L, reason, admin.getEmail()));
            assertThrows(InvalidModerationReasonException.class, () -> moderation.updateOrganizationStatus(7L, false, reason, admin.getEmail()));
        }
        verify(organizations, never()).save(any());
    }
    @ParameterizedTest @ValueSource(ints = {3, 500})
    void normalizesAndPersistsReasonsAtBothLimits(int length) {
        String reason = "a".repeat(length);
        assertEquals(reason, moderation.rejectOrganization(7L, "  " + reason + "  ", admin.getEmail()).getModerationReason());
        assertEquals(reason, org.getModerationReason()); verify(organizations).save(org);
    }
    @Test void repeatRejectionUpdatesReasonAndApprovalClearsReason() {
        moderation.rejectOrganization(7L, "Cadastro incompleto", admin.getEmail());
        moderation.rejectOrganization(7L, "Dados insuficientes", admin.getEmail());
        assertEquals("Dados insuficientes", org.getModerationReason());
        assertNull(moderation.approveOrganization(7L, admin.getEmail()).getModerationReason());
        moderation.approveOrganization(7L, admin.getEmail());
        assertTrue(org.isApproved()); assertEquals("APPROVED", org.getStatus()); assertNull(org.getModerationReason());
    }
    @Test void blockingStoresReasonAndReactivationClearsIt() {
        org.setApproved(true); org.setStatus("APPROVED");
        moderation.updateOrganizationStatus(7L, false, "Documentação irregular", admin.getEmail());
        assertEquals("BLOCKED", org.getStatus()); assertEquals("Documentação irregular", org.getModerationReason());
        moderation.updateOrganizationStatus(7L, true, null, admin.getEmail());
        assertNull(org.getModerationReason()); assertTrue(org.isApproved());
    }
    @Test void reasonIsProjectedOnlyForTheCurrentOwnerAndAdministrators() {
        org.setModerationReason("Motivo reservado");
        assertEquals("Motivo reservado", OrganizationResponseDTO.forViewer(org, owner).getModerationReason());
        assertEquals("Motivo reservado", OrganizationResponseDTO.forViewer(org, admin).getModerationReason());
        assertNull(OrganizationResponseDTO.forViewer(org, member).getModerationReason());
        assertNull(OrganizationResponseDTO.fromEntity(org).getModerationReason());
        org.setOwner(member);
        assertNull(OrganizationResponseDTO.forViewer(org, owner).getModerationReason());
        assertEquals("Motivo reservado", OrganizationResponseDTO.forViewer(org, member).getModerationReason());
    }
    @Test void nonAdministratorsCannotModerate() {
        assertThrows(SecurityException.class, () -> moderation.rejectOrganization(7L, "Motivo válido", member.getEmail()));
        assertThrows(SecurityException.class, () -> moderation.approveOrganization(7L, owner.getEmail()));
        assertThrows(SecurityException.class, () -> moderation.updateOrganizationStatus(7L, false, "Motivo válido", member.getEmail()));
        verify(organizations, never()).save(any());
    }
    @Test void requestingAndReopeningAccessKeepsOnePendingRequest() {
        org.setApproved(true); org.setStatus("APPROVED");
        membership.requestAccess(member.getEmail(), "#EquipeQA");
        verify(requests).save(any());
        AccessRequest previous = request(); previous.setStatus(AccessRequest.AccessRequestStatus.REJECTED);
        when(requests.findByUserIdAndOrganizationId(member.getId(), org.getId())).thenReturn(List.of(previous));
        membership.requestAccess(member.getEmail(), "#EquipeQA");
        assertEquals(AccessRequest.AccessRequestStatus.PENDING, previous.getStatus());
        assertThrows(IllegalStateException.class, () -> membership.requestAccess(member.getEmail(), "#EquipeQA"));
        verify(requests, times(2)).save(any()); verify(organizations, times(3)).findByIdForUpdate(org.getId());
    }
    private AccessRequest request() {
        AccessRequest request = new AccessRequest(); request.setId(9L); request.setUser(member); request.setOrganization(org);
        request.setStatus(AccessRequest.AccessRequestStatus.PENDING);
        when(requests.findOrganizationIdById(9L)).thenReturn(Optional.of(7L)); when(requests.findById(9L)).thenReturn(Optional.of(request));
        return request;
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void requestDecisionsCannotBeRepeated(boolean approve) {
        org.setApproved(true); AccessRequest request = request();
        if (approve) membership.approveAccess(9L, owner.getEmail()); else membership.rejectAccess(9L, owner.getEmail());
        assertEquals(approve ? AccessRequest.AccessRequestStatus.APPROVED : AccessRequest.AccessRequestStatus.REJECTED, request.getStatus());
        assertThrows(IllegalStateException.class, () -> membership.approveAccess(9L, owner.getEmail()));
        verify(requests).save(request);
    }
    @Test void outsiderCannotDecideAnAccessRequest() {
        org.setApproved(true); request(); org.setOwner(user(8, "other", "USER"));
        assertThrows(SecurityException.class, () -> membership.approveAccess(9L, owner.getEmail())); verify(requests, never()).save(any());
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"reason\":\"ab\"}", "{\"reason\":null}", "{\"reason\":{}}", "{broken"})
    void malformedRejectionBodiesReturn400WithoutWriting(String body) throws Exception {
        mvc.perform(post("/api/admin/organizations/7/reject").principal(principal(admin)).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verify(organizations, never()).save(any());
    }
    @Test void blockingWithoutReasonReturns400AndApproveDoesNotRequireAReason() throws Exception {
        mvc.perform(patch("/api/admin/organizations/7/status").principal(principal(admin)).contentType("application/json").content("{\"approved\":false}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/organizations/7/status").principal(principal(admin)).contentType("application/json").content("{\"approved\":true}")).andExpect(status().isOk());
        verify(organizations).save(org);
    }
}
