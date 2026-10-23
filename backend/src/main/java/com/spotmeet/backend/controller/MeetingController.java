package com.spotmeet.backend.controller;

import com.spotmeet.backend.dto.MeetingRequestDTO;
import com.spotmeet.backend.dto.MeetingResponseDTO;
import com.spotmeet.backend.service.MeetingService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Scheduling routes: the committee president schedules, edits and cancels.
 * Active members and organization leaders read meetings.
 */
@RestController
@CrossOrigin(origins = "*")
public class MeetingController {

    @Autowired
    private MeetingService meetingService;

    private String getEmail(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new SecurityException("Usuário não autenticado.");
        }
        return auth.getName();
    }

    /** POST /api/committees/{committeeId}/meetings: schedules a meeting (president only). */
    @PostMapping("/api/committees/{committeeId}/meetings")
    public ResponseEntity<MeetingResponseDTO> createMeeting(
            @PathVariable Long committeeId,
            @Valid @RequestBody MeetingRequestDTO dto,
            Authentication auth) {
        MeetingResponseDTO response = meetingService.createMeeting(committeeId, getEmail(auth), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** GET /api/committees/{committeeId}/meetings: meetings of the committee. */
    @GetMapping("/api/committees/{committeeId}/meetings")
    public ResponseEntity<List<MeetingResponseDTO>> listCommitteeMeetings(
            @PathVariable Long committeeId,
            Authentication auth) {
        return ResponseEntity.ok(meetingService.listCommitteeMeetings(committeeId, getEmail(auth)));
    }

    /** GET /api/meetings/mine: meetings the authenticated user was invited to. */
    @GetMapping("/api/meetings/mine")
    public ResponseEntity<List<MeetingResponseDTO>> listMyMeetings(Authentication auth) {
        return ResponseEntity.ok(meetingService.listMyMeetings(getEmail(auth)));
    }

    /** GET /api/meetings/{meetingId}: meeting detail with participants. */
    @GetMapping("/api/meetings/{meetingId}")
    public ResponseEntity<MeetingResponseDTO> getMeeting(
            @PathVariable Long meetingId,
            Authentication auth) {
        return ResponseEntity.ok(meetingService.getMeeting(meetingId, getEmail(auth)));
    }

    /** PUT /api/meetings/{meetingId}: edits a meeting that has not started (president only). */
    @PutMapping("/api/meetings/{meetingId}")
    public ResponseEntity<MeetingResponseDTO> updateMeeting(
            @PathVariable Long meetingId,
            @Valid @RequestBody MeetingRequestDTO dto,
            Authentication auth) {
        return ResponseEntity.ok(meetingService.updateMeeting(meetingId, getEmail(auth), dto));
    }

    /** POST /api/meetings/{meetingId}/cancel: cancels a meeting (president only). */
    @PostMapping("/api/meetings/{meetingId}/cancel")
    public ResponseEntity<MeetingResponseDTO> cancelMeeting(
            @PathVariable Long meetingId,
            Authentication auth) {
        return ResponseEntity.ok(meetingService.cancelMeeting(meetingId, getEmail(auth)));
    }
}
