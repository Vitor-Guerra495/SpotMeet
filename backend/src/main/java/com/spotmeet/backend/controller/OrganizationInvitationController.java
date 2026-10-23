package com.spotmeet.backend.controller;

import com.spotmeet.backend.dto.OrganizationInvitationRequestDTO;
import com.spotmeet.backend.dto.RespondOrganizationInvitationDTO;
import com.spotmeet.backend.service.OrganizationInvitationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
public class OrganizationInvitationController {
    @Autowired private OrganizationInvitationService invitationService;

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> invalidRequest() {
        return ResponseEntity.badRequest().body("Dados do convite inválidos.");
    }

    @PostMapping("/api/organizations/{organizationId}/invitations")
    public ResponseEntity<?> invite(@PathVariable Long organizationId,
            @Valid @RequestBody OrganizationInvitationRequestDTO dto, Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(invitationService.invite(organizationId, dto, authentication.getName()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Não foi possível enviar o convite.");
        }
    }

    @GetMapping("/api/organizations/{organizationId}/invitations/candidates")
    public ResponseEntity<?> candidates(@PathVariable Long organizationId, @RequestParam(defaultValue = "") String q,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        try {
            return ResponseEntity.ok(invitationService.findCandidates(organizationId, q, authentication.getName()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Não foi possível buscar as pessoas.");
        }
    }

    @GetMapping("/api/organization-invitations/mine")
    public ResponseEntity<?> listMine(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        try {
            return ResponseEntity.ok(invitationService.listMine(authentication.getName()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Não foi possível consultar os convites.");
        }
    }

    @PostMapping("/api/organization-invitations/{invitationId}/respond")
    public ResponseEntity<?> respond(@PathVariable Long invitationId,
            @Valid @RequestBody RespondOrganizationInvitationDTO dto, Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        try {
            return ResponseEntity.ok(invitationService.respond(invitationId, dto.getAccept(), authentication.getName()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Não foi possível responder ao convite.");
        }
    }
}
