package com.spotmeet.backend.controller;

import com.spotmeet.backend.dto.OrganizationRequestDTO;
import com.spotmeet.backend.dto.OrganizationResponseDTO;
import com.spotmeet.backend.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Organization controller, protected routes (valid JWT required).
 *
 * Least privilege:
 *   - Owner e-mail taken from the JWT (Authentication), never from the body.
 *   - No sensitive field (approved, owner_id) accepted via request.
 */
@RestController
@RequestMapping("/api/organizations")
@CrossOrigin(origins = "*")
public class OrganizationController {

    @Autowired
    private OrganizationService organizationService;

    /**
     * POST /api/organizations
     *
     * @return 201 CREATED with the organization data.
     *         409 CONFLICT if the name, access key or CNPJ is already in use.
     *         400 BAD_REQUEST if the DTO is invalid.
     */
    @PostMapping
    public ResponseEntity<?> createOrganization(
            @Valid @RequestBody OrganizationRequestDTO dto,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        try {
            String ownerEmail = authentication.getName();
            OrganizationResponseDTO response = organizationService.createOrganization(ownerEmail, dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    /**
     * GET /api/organizations/mine
     *
     * @return 200 OK with the organizations of the authenticated user.
     */
    @GetMapping("/mine")
    public ResponseEntity<?> listMyOrganizations(
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        String email = authentication.getName();
        return ResponseEntity.ok(organizationService.listMyOrganizations(email));
    }

    /**
     * PUT /api/organizations/{organizationId}
     * Updates name, access key and CNPJ. Allowed only for owners and vice owners.
     *
     * @return 200 OK with the updated organization.
     *         409 CONFLICT if the name, access key or CNPJ belongs to another organization.
     */
    @PutMapping("/{organizationId}")
    public ResponseEntity<?> updateOrganization(
            @PathVariable Long organizationId,
            @Valid @RequestBody OrganizationRequestDTO dto,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        try {
            String email = authentication.getName();
            return ResponseEntity.ok(organizationService.updateOrganization(organizationId, email, dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    /**
     * POST /api/organizations/{organizationId}/resubmit
     * Sends a rejected organization back for review, once, with the owner's justification.
     *
     * @return 200 OK with the organization (status PENDING).
     *         403 FORBIDDEN if the user is not the owner.
     *         409 CONFLICT if the organization is not rejected, the review was already used or the justification is invalid.
     */
    @PostMapping("/{organizationId}/resubmit")
    public ResponseEntity<?> resubmitOrganization(
            @PathVariable Long organizationId,
            @Valid @RequestBody com.spotmeet.backend.dto.OrganizationResubmitDTO body,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        return ResponseEntity.ok(organizationService.resubmitOrganization(organizationId, authentication.getName(), body.getJustification()));
    }

    /**
     * DELETE /api/organizations/{organizationId}
     * Permanently deletes the organization and all associated data.
     * Allowed only for owners and vice owners.
     */
    @DeleteMapping("/{organizationId}")
    public ResponseEntity<?> deleteOrganization(
            @PathVariable Long organizationId,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        String email = authentication.getName();
        organizationService.deleteOrganization(organizationId, email);
        return ResponseEntity.ok("Organização excluída com sucesso.");
    }
}
