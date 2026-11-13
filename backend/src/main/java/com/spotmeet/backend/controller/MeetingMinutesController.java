package com.spotmeet.backend.controller;

import com.spotmeet.backend.dto.MinutesAnswerDTO;
import com.spotmeet.backend.dto.MinutesRequestDTO;
import com.spotmeet.backend.dto.MinutesResponseDTO;
import com.spotmeet.backend.service.MeetingMinutesService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Minutes routes. The president writes, sends and closes; present people approve or reject.
 */
@RestController
@CrossOrigin(origins = "*")
public class MeetingMinutesController {

    @Autowired
    private MeetingMinutesService minutesService;

    private String getEmail(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new SecurityException("Usuário não autenticado.");
        }
        return auth.getName();
    }

    /** GET /api/meetings/{meetingId}/minutes: minutes of the meeting, with present and absent people. */
    @GetMapping("/api/meetings/{meetingId}/minutes")
    public ResponseEntity<MinutesResponseDTO> getMinutes(@PathVariable Long meetingId, Authentication auth) {
        return ResponseEntity.ok(minutesService.get(meetingId, getEmail(auth)));
    }

    /** PUT /api/meetings/{meetingId}/minutes: saves the draft (president only). */
    @PutMapping("/api/meetings/{meetingId}/minutes")
    public ResponseEntity<MinutesResponseDTO> saveDraft(
            @PathVariable Long meetingId,
            @Valid @RequestBody MinutesRequestDTO dto,
            Authentication auth) {
        return ResponseEntity.ok(minutesService.saveDraft(meetingId, getEmail(auth), dto));
    }

    /** POST /api/meetings/{meetingId}/minutes/submit: sends for approval, or sends again after a rejection (president only). */
    @PostMapping("/api/meetings/{meetingId}/minutes/submit")
    public ResponseEntity<MinutesResponseDTO> submit(
            @PathVariable Long meetingId,
            @Valid @RequestBody MinutesRequestDTO dto,
            Authentication auth) {
        return ResponseEntity.ok(minutesService.submit(meetingId, getEmail(auth), dto));
    }

    /** POST /api/meetings/{meetingId}/minutes/answer: approves or rejects with a reason (present people). */
    @PostMapping("/api/meetings/{meetingId}/minutes/answer")
    public ResponseEntity<MinutesResponseDTO> answer(
            @PathVariable Long meetingId,
            @Valid @RequestBody MinutesAnswerDTO dto,
            Authentication auth) {
        return ResponseEntity.ok(minutesService.answer(meetingId, getEmail(auth), dto));
    }

    /** POST /api/meetings/{meetingId}/minutes/close: closes the approval (president only). */
    @PostMapping("/api/meetings/{meetingId}/minutes/close")
    public ResponseEntity<MinutesResponseDTO> close(@PathVariable Long meetingId, Authentication auth) {
        return ResponseEntity.ok(minutesService.close(meetingId, getEmail(auth)));
    }
}
