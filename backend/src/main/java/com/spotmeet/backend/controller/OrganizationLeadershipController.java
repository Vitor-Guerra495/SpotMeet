package com.spotmeet.backend.controller;

import com.spotmeet.backend.service.MembershipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
public class OrganizationLeadershipController {
    @Autowired private MembershipService membershipService;

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> invalidIdentifier() {
        return ResponseEntity.badRequest().body("Identificadores da transferência inválidos.");
    }

    @PostMapping("/api/organizations/{organizationId}/members/{userId}/make-owner")
    public ResponseEntity<?> transfer(@PathVariable Long organizationId, @PathVariable Long userId, Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
        }
        if (organizationId <= 0 || userId <= 0) {
            return ResponseEntity.badRequest().body("Identificadores da transferência devem ser positivos.");
        }
        try {
            return ResponseEntity.ok(membershipService.transferLeadership(organizationId, userId, authentication.getName()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Não foi possível transferir a liderança.");
        }
    }
}
