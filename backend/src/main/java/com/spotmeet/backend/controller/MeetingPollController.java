package com.spotmeet.backend.controller;

import com.spotmeet.backend.dto.PollRequestDTO;
import com.spotmeet.backend.dto.PollResponseDTO;
import com.spotmeet.backend.dto.PollVoteDTO;
import com.spotmeet.backend.service.MeetingPollService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Vote routes. The president opens and closes; present people vote. Every route answers with all votes of the meeting.
 */
@RestController
@CrossOrigin(origins = "*")
public class MeetingPollController {

    @Autowired
    private MeetingPollService pollService;

    private String getEmail(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new SecurityException("Usuário não autenticado.");
        }
        return auth.getName();
    }

    /** GET /api/meetings/{meetingId}/polls: votes of the meeting. */
    @GetMapping("/api/meetings/{meetingId}/polls")
    public ResponseEntity<List<PollResponseDTO>> listPolls(@PathVariable Long meetingId, Authentication auth) {
        return ResponseEntity.ok(pollService.list(meetingId, getEmail(auth)));
    }

    /** POST /api/meetings/{meetingId}/polls: opens a vote during the meeting (president only). */
    @PostMapping("/api/meetings/{meetingId}/polls")
    public ResponseEntity<List<PollResponseDTO>> openPoll(
            @PathVariable Long meetingId,
            @Valid @RequestBody PollRequestDTO dto,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pollService.open(meetingId, getEmail(auth), dto));
    }

    /** POST /api/meetings/{meetingId}/polls/{pollId}/vote: registers the vote of a present person. */
    @PostMapping("/api/meetings/{meetingId}/polls/{pollId}/vote")
    public ResponseEntity<List<PollResponseDTO>> vote(
            @PathVariable Long meetingId,
            @PathVariable Long pollId,
            @Valid @RequestBody PollVoteDTO dto,
            Authentication auth) {
        return ResponseEntity.ok(pollService.vote(meetingId, pollId, getEmail(auth), dto));
    }

    /** POST /api/meetings/{meetingId}/polls/{pollId}/close: closes the vote (president only). */
    @PostMapping("/api/meetings/{meetingId}/polls/{pollId}/close")
    public ResponseEntity<List<PollResponseDTO>> closePoll(
            @PathVariable Long meetingId,
            @PathVariable Long pollId,
            Authentication auth) {
        return ResponseEntity.ok(pollService.close(meetingId, pollId, getEmail(auth)));
    }
}
