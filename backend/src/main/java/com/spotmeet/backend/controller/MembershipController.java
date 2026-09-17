package com.spotmeet.backend.controller;

import com.spotmeet.backend.dto.AccessRequestDTO;
import com.spotmeet.backend.dto.AccessRequestResponseDTO;
import com.spotmeet.backend.dto.OrganizationResponseDTO;
import com.spotmeet.backend.service.MembershipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Organization access and membership controller.
 *
 * Least privilege:
 *   - Executor e-mail always taken from the JWT (Authentication).
 *   - Management operations restricted to leaders / SYSADMIN.
 */
@RestController
@CrossOrigin(origins = "*")
public class MembershipController {

    @Autowired
    private MembershipService membershipService;

    // Member routes

    /**
     * POST /api/access-requests
     * Accepts a JSON body { "accessKey": "#aaa" } or the query param ?accessKey=#aaa
     */
    @PostMapping("/api/access-requests")
    public ResponseEntity<?> requestAccess(
            @RequestBody(required = false) AccessRequestDTO body,
            @RequestParam(required = false) String accessKey,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario nao autenticado.");
        }

        String finalKey = (body != null && body.getAccessKey() != null) ? body.getAccessKey() : accessKey;
        if (finalKey == null || finalKey.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Chave da organizacao e obrigatoria.");
        }

        try {
            String email = authentication.getName();
            AccessRequestResponseDTO response = membershipService.requestAccess(email, finalKey);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    // Leader routes (ORG_OWNER / ORG_VICE_OWNER / SYSADMIN)

    @PostMapping("/api/access-requests/{requestId}/approve")
    public ResponseEntity<?> approveAccess(
            @PathVariable Long requestId,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario nao autenticado.");
        }
        try {
            String leaderEmail = authentication.getName();
            AccessRequestResponseDTO response = membershipService.approveAccess(requestId, leaderEmail);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    @PostMapping("/api/access-requests/{requestId}/reject")
    public ResponseEntity<?> rejectAccess(
            @PathVariable Long requestId,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario nao autenticado.");
        }
        try {
            String leaderEmail = authentication.getName();
            AccessRequestResponseDTO response = membershipService.rejectAccess(requestId, leaderEmail);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    /**
     * DELETE /api/organizations/{organizationId}/members/{memberId}
     * Revokes the access of an active member.
     */
    @DeleteMapping("/api/organizations/{organizationId}/members/{memberId}")
    public ResponseEntity<?> revokeAccess(
            @PathVariable Long organizationId,
            @PathVariable Long memberId,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario nao autenticado.");
        }
        try {
            String executorEmail = authentication.getName();
            membershipService.revokeAccess(memberId, organizationId, executorEmail);
            return ResponseEntity.ok("Acesso revogado com sucesso.");
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * GET /api/organizations/{organizationId}/access-requests
     * Lists pending access requests (owner and vice owner).
     */
    @GetMapping("/api/organizations/{organizationId}/access-requests")
    public ResponseEntity<?> listPendingRequests(
            @PathVariable Long organizationId,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario nao autenticado.");
        }
        try {
            String executorEmail = authentication.getName();
            List<AccessRequestResponseDTO> list = membershipService.listPendingRequests(organizationId, executorEmail);
            return ResponseEntity.ok(list);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    /**
     * GET /api/organizations/{organizationId}/members
     * Lists active members of the organization (visible to every member).
     */
    @GetMapping("/api/organizations/{organizationId}/members")
    public ResponseEntity<?> listMembers(
            @PathVariable Long organizationId,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario nao autenticado.");
        }
        try {
            String executorEmail = authentication.getName();
            List<OrganizationResponseDTO.MemberDTO> list = membershipService.listMembers(organizationId, executorEmail);
            return ResponseEntity.ok(list);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    // Role promotion / demotion routes

    /**
     * POST /api/organizations/{organizationId}/members/{userId}/promote
     * Promotes MEMBER to ORG_SUBOWNER (owner or vice owner)
     * or ORG_SUBOWNER to ORG_VICE_OWNER (owner only).
     */
    @PostMapping("/api/organizations/{organizationId}/members/{userId}/promote")
    public ResponseEntity<?> promoteMember(
            @PathVariable Long organizationId,
            @PathVariable Long userId,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario nao autenticado.");
        }
        try {
            String email = authentication.getName();
            OrganizationResponseDTO.MemberDTO member = membershipService.promoteMember(organizationId, userId, email);
            return ResponseEntity.ok(member);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    /**
     * POST /api/organizations/{organizationId}/members/{userId}/demote
     * Demotes ORG_VICE_OWNER to ORG_SUBOWNER (owner only)
     * or ORG_SUBOWNER to MEMBER (owner or vice owner).
     */
    @PostMapping("/api/organizations/{organizationId}/members/{userId}/demote")
    public ResponseEntity<?> demoteMember(
            @PathVariable Long organizationId,
            @PathVariable Long userId,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario nao autenticado.");
        }
        try {
            String email = authentication.getName();
            OrganizationResponseDTO.MemberDTO member = membershipService.demoteMember(organizationId, userId, email);
            return ResponseEntity.ok(member);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }
}
