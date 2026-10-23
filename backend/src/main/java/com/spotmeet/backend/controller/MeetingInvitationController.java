package com.spotmeet.backend.controller;

import com.spotmeet.backend.dto.MeetingAnswerDTO;
import com.spotmeet.backend.dto.MeetingResponseDTO;
import com.spotmeet.backend.service.MeetingInvitationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Answer to a meeting invitation by an invited member. */
@RestController
@CrossOrigin(origins = "*")
public class MeetingInvitationController {

    @Autowired
    private MeetingInvitationService invitationService;

    /** POST /api/meetings/{meetingId}/respond?accept=true|false, with an optional comment in the body. */
    @PostMapping("/api/meetings/{meetingId}/respond")
    public ResponseEntity<MeetingResponseDTO> respond(
            @PathVariable Long meetingId,
            @RequestParam boolean accept,
            @Valid @RequestBody(required = false) MeetingAnswerDTO answer,
            Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new SecurityException("Usuário não autenticado.");
        }
        String comment = answer != null ? answer.getComment() : null;
        return ResponseEntity.ok(invitationService.respond(meetingId, auth.getName(), accept, comment));
    }
}
