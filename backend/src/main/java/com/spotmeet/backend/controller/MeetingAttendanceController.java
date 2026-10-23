package com.spotmeet.backend.controller;

import com.spotmeet.backend.dto.MeetingResponseDTO;
import com.spotmeet.backend.service.MeetingAttendanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Entry and exit routes. The person registers their own record; the president registers for anyone.
 */
@RestController
@CrossOrigin(origins = "*")
public class MeetingAttendanceController {

    @Autowired
    private MeetingAttendanceService attendanceService;

    private String getEmail(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new SecurityException("Usuário não autenticado.");
        }
        return auth.getName();
    }

    /** POST /api/meetings/{meetingId}/participants/{userId}/entry: registers an entry. */
    @PostMapping("/api/meetings/{meetingId}/participants/{userId}/entry")
    public ResponseEntity<MeetingResponseDTO> registerEntry(
            @PathVariable Long meetingId,
            @PathVariable Long userId,
            Authentication auth) {
        return ResponseEntity.ok(attendanceService.registerEntry(meetingId, userId, getEmail(auth)));
    }

    /** POST /api/meetings/{meetingId}/participants/{userId}/exit: registers an exit. */
    @PostMapping("/api/meetings/{meetingId}/participants/{userId}/exit")
    public ResponseEntity<MeetingResponseDTO> registerExit(
            @PathVariable Long meetingId,
            @PathVariable Long userId,
            Authentication auth) {
        return ResponseEntity.ok(attendanceService.registerExit(meetingId, userId, getEmail(auth)));
    }

    /** POST /api/meetings/{meetingId}/attendance/entries: entry of all who accepted (president only). */
    @PostMapping("/api/meetings/{meetingId}/attendance/entries")
    public ResponseEntity<MeetingResponseDTO> registerAllEntries(
            @PathVariable Long meetingId,
            Authentication auth) {
        return ResponseEntity.ok(attendanceService.registerAllEntries(meetingId, getEmail(auth)));
    }

    /** POST /api/meetings/{meetingId}/attendance/exits: exit of all who are inside (president only). */
    @PostMapping("/api/meetings/{meetingId}/attendance/exits")
    public ResponseEntity<MeetingResponseDTO> registerAllExits(
            @PathVariable Long meetingId,
            Authentication auth) {
        return ResponseEntity.ok(attendanceService.registerAllExits(meetingId, getEmail(auth)));
    }

    /** DELETE /api/meetings/{meetingId}/participants/{userId}/attendance: clears the records (president only). */
    @DeleteMapping("/api/meetings/{meetingId}/participants/{userId}/attendance")
    public ResponseEntity<MeetingResponseDTO> resetAttendance(
            @PathVariable Long meetingId,
            @PathVariable Long userId,
            Authentication auth) {
        return ResponseEntity.ok(attendanceService.resetAttendance(meetingId, userId, getEmail(auth)));
    }
}
