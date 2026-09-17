package com.spotmeet.backend.controller;

import com.spotmeet.backend.dto.CommitteeMemberResponseDTO;
import com.spotmeet.backend.dto.CommitteeRequestDTO;
import com.spotmeet.backend.dto.CommitteeResponseDTO;
import com.spotmeet.backend.dto.UserCommitteeLinkDTO;
import com.spotmeet.backend.service.CommitteeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Committee controller.
 *
 * Governance:
 *   - Leaders (ORG_OWNER / ORG_VICE_OWNER / ORG_SUBOWNER) manage committees and member inclusion.
 *   - Members respond to pending invitations and may request to join.
 */
@RestController
@CrossOrigin(origins = "*")
public class CommitteeController {

    @Autowired
    private CommitteeService committeeService;

    private String getEmail(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new SecurityException("Usuario nao autenticado.");
        }
        return auth.getName();
    }

    // Leader routes: committee structure

    /**
     * POST /api/organizations/{organizationId}/committees
     * Creates a new committee in the organization (leader only).
     */
    @PostMapping("/api/organizations/{organizationId}/committees")
    public ResponseEntity<CommitteeResponseDTO> createCommittee(
            @PathVariable Long organizationId,
            @Valid @RequestBody CommitteeRequestDTO dto,
            Authentication auth) {
        String email = getEmail(auth);
        CommitteeResponseDTO response = committeeService.createCommittee(organizationId, email, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/organizations/{organizationId}/committees
     * Lists the committees of the organization with active and pending member counts.
     */
    @GetMapping("/api/organizations/{organizationId}/committees")
    public ResponseEntity<List<CommitteeResponseDTO>> listCommittees(
            @PathVariable Long organizationId,
            Authentication auth) {
        String email = getEmail(auth);
        return ResponseEntity.ok(committeeService.listCommittees(organizationId, email));
    }

    /**
     * PUT /api/committees/{committeeId}
     * Updates the name and description of a committee (leader only).
     */
    @PutMapping("/api/committees/{committeeId}")
    public ResponseEntity<CommitteeResponseDTO> updateCommittee(
            @PathVariable Long committeeId,
            @Valid @RequestBody CommitteeRequestDTO dto,
            Authentication auth) {
        String email = getEmail(auth);
        return ResponseEntity.ok(committeeService.updateCommittee(committeeId, email, dto));
    }

    /**
     * DELETE /api/committees/{committeeId}
     * Deletes a committee and all its memberships (leader only).
     */
    @DeleteMapping("/api/committees/{committeeId}")
    public ResponseEntity<?> deleteCommittee(
            @PathVariable Long committeeId,
            Authentication auth) {
        String email = getEmail(auth);
        committeeService.deleteCommittee(committeeId, email);
        return ResponseEntity.ok("Comissao excluida com sucesso.");
    }

    // Leader routes: per-member committee links (modal)

    /**
     * GET /api/organizations/{organizationId}/members/{userId}/committees
     * Returns every committee of the organization with the membership state of the given user.
     */
    @GetMapping("/api/organizations/{organizationId}/members/{userId}/committees")
    public ResponseEntity<List<UserCommitteeLinkDTO>> listUserCommitteeLinks(
            @PathVariable Long organizationId,
            @PathVariable Long userId,
            Authentication auth) {
        String email = getEmail(auth);
        return ResponseEntity.ok(committeeService.listUserCommitteeLinks(organizationId, userId, email));
    }

    /**
     * POST /api/committees/{committeeId}/members/{userId}
     * Leader adds a member to the committee (creates a pending invitation for the user).
     */
    @PostMapping("/api/committees/{committeeId}/members/{userId}")
    public ResponseEntity<CommitteeMemberResponseDTO> addUserToCommittee(
            @PathVariable Long committeeId,
            @PathVariable Long userId,
            Authentication auth) {
        String email = getEmail(auth);
        CommitteeMemberResponseDTO response = committeeService.addUserToCommittee(committeeId, userId, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * DELETE /api/committees/{committeeId}/members/{userId}
     * Leader removes a member from the committee.
     */
    @DeleteMapping("/api/committees/{committeeId}/members/{userId}")
    public ResponseEntity<?> removeUserFromCommittee(
            @PathVariable Long committeeId,
            @PathVariable Long userId,
            Authentication auth) {
        String email = getEmail(auth);
        committeeService.removeUserFromCommittee(committeeId, userId, email);
        return ResponseEntity.ok("Acesso a comissao revogado com sucesso.");
    }

    // Member routes: pending invitations

    /**
     * GET /api/committees/invitations
     * Lists the pending committee invitations of the authenticated user.
     */
    @GetMapping("/api/committees/invitations")
    public ResponseEntity<List<CommitteeMemberResponseDTO>> listMyInvitations(Authentication auth) {
        String email = getEmail(auth);
        return ResponseEntity.ok(committeeService.listMyInvitations(email));
    }

    /**
     * POST /api/committees/{committeeId}/invitations/respond?accept=true|false
     * Member accepts or declines the committee invitation.
     */
    @PostMapping("/api/committees/{committeeId}/invitations/respond")
    public ResponseEntity<CommitteeMemberResponseDTO> respondInvitation(
            @PathVariable Long committeeId,
            @RequestParam boolean accept,
            Authentication auth) {
        String email = getEmail(auth);
        return ResponseEntity.ok(committeeService.respondInvitation(committeeId, email, accept));
    }

    // Direct join request routes

    /**
     * POST /api/committees/{committeeId}/join-requests
     * User requests to join the committee (pending leader approval).
     */
    @PostMapping("/api/committees/{committeeId}/join-requests")
    public ResponseEntity<CommitteeMemberResponseDTO> requestToJoin(
            @PathVariable Long committeeId,
            Authentication auth) {
        String email = getEmail(auth);
        CommitteeMemberResponseDTO response = committeeService.requestToJoin(committeeId, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/committees/{committeeId}/join-requests
     * Leader lists join requests pending approval.
     */
    @GetMapping("/api/committees/{committeeId}/join-requests")
    public ResponseEntity<List<CommitteeMemberResponseDTO>> listPendingJoinRequests(
            @PathVariable Long committeeId,
            Authentication auth) {
        String email = getEmail(auth);
        return ResponseEntity.ok(committeeService.listPendingJoinRequests(committeeId, email));
    }

    /**
     * POST /api/committees/join-requests/{membershipId}/respond?approve=true|false
     * Leader approves or rejects the join request.
     */
    @PostMapping("/api/committees/join-requests/{membershipId}/respond")
    public ResponseEntity<CommitteeMemberResponseDTO> respondJoinRequest(
            @PathVariable Long membershipId,
            @RequestParam boolean approve,
            Authentication auth) {
        String email = getEmail(auth);
        return ResponseEntity.ok(committeeService.respondJoinRequest(membershipId, email, approve));
    }

    /**
     * GET /api/organizations/{organizationId}/committees/join-requests
     * Lists every committee join request pending approval in the organization.
     * Visible to owner, vice owner and subowner.
     */
    @GetMapping("/api/organizations/{organizationId}/committees/join-requests")
    public ResponseEntity<List<CommitteeMemberResponseDTO>> listPendingJoinRequestsForOrganization(
            @PathVariable Long organizationId,
            Authentication auth) {
        String email = getEmail(auth);
        return ResponseEntity.ok(committeeService.listPendingJoinRequestsForOrganization(organizationId, email));
    }
}
