package com.spotmeet.backend.service;

import com.spotmeet.backend.dto.MeetingResponseDTO;
import com.spotmeet.backend.model.*;
import com.spotmeet.backend.model.Meeting.MeetingStatus;
import com.spotmeet.backend.model.MeetingParticipant.InvitationResponse;
import com.spotmeet.backend.repository.MeetingParticipantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entry and exit records of a meeting.
 *
 * Rules:
 *   - The person registers their own entry and exit, and the president can register for anyone.
 *   - Records are only accepted while the meeting is in progress.
 *   - One entry and one exit per person; only the president clears a record.
 *   - Each record keeps who made it.
 */
@Service
public class MeetingAttendanceService {

    @Autowired private MeetingParticipantRepository participantRepo;
    @Autowired private MeetingAccess access;

    private void requireInProgress(Meeting meeting, LocalDateTime now) {
        if (meeting.statusAt(now) != MeetingStatus.IN_PROGRESS) {
            throw new IllegalStateException("O registro só é aceito enquanto a reunião está em andamento.");
        }
    }

    private MeetingParticipant lockParticipantForRecord(Meeting meeting, User actor, Long targetUserId, LocalDateTime now) {
        Committee committee = meeting.getCommittee();
        if (actor.getId().equals(targetUserId)) {
            access.requireActiveMember(actor, committee);
        } else {
            access.requirePresident(actor, committee);
        }
        requireInProgress(meeting, now);
        return participantRepo.findForUpdate(meeting.getId(), targetUserId)
                .orElseThrow(() -> new IllegalStateException("Este usuário não foi convidado para a reunião."));
    }

    @Transactional
    public MeetingResponseDTO registerEntry(Long meetingId, Long targetUserId, String email) {
        Meeting meeting = access.findMeeting(meetingId);
        User actor = access.findUser(email);
        LocalDateTime now = LocalDateTime.now();
        MeetingParticipant participant = lockParticipantForRecord(meeting, actor, targetUserId, now);
        if (participant.getEnteredAt() != null) {
            throw new IllegalStateException("A entrada já foi registrada.");
        }
        participant.setEnteredAt(now);
        participant.setEnteredBy(actor);
        participantRepo.save(participant);
        access.invalidateList(meeting);

        access.audit(actor, "MEETING_ENTRY_REGISTERED", meeting, " | Usuário ID: " + targetUserId);
        return access.detail(meeting, actor, now);
    }

    @Transactional
    public MeetingResponseDTO registerExit(Long meetingId, Long targetUserId, String email) {
        Meeting meeting = access.findMeeting(meetingId);
        User actor = access.findUser(email);
        LocalDateTime now = LocalDateTime.now();
        MeetingParticipant participant = lockParticipantForRecord(meeting, actor, targetUserId, now);
        if (participant.getEnteredAt() == null) {
            throw new IllegalStateException("Registre a entrada antes da saída.");
        }
        if (participant.getLeftAt() != null) {
            throw new IllegalStateException("A saída já foi registrada.");
        }
        participant.setLeftAt(now);
        participant.setLeftBy(actor);
        participantRepo.save(participant);
        access.invalidateList(meeting);

        access.audit(actor, "MEETING_EXIT_REGISTERED", meeting, " | Usuário ID: " + targetUserId);
        return access.detail(meeting, actor, now);
    }

    /** Entry of everyone who accepted and has no entry yet (president only). */
    @Transactional
    public MeetingResponseDTO registerAllEntries(Long meetingId, String email) {
        Meeting meeting = access.findMeeting(meetingId);
        User president = access.findUser(email);
        access.requirePresident(president, meeting.getCommittee());
        LocalDateTime now = LocalDateTime.now();
        requireInProgress(meeting, now);

        List<MeetingParticipant> pending = participantRepo.findByMeetingIdOrderByUserNameAsc(meetingId).stream()
                .filter(p -> p.getResponse() == InvitationResponse.ACCEPTED && p.getEnteredAt() == null).toList();
        for (MeetingParticipant p : pending) {
            p.setEnteredAt(now);
            p.setEnteredBy(president);
        }
        participantRepo.saveAll(pending);
        if (!pending.isEmpty()) access.invalidateList(meeting);

        access.audit(president, "MEETING_ENTRIES_REGISTERED_BULK", meeting, " | Pessoas: " + pending.size());
        return access.detail(meeting, president, now);
    }

    /** Exit of everyone who entered and has no exit yet (president only). */
    @Transactional
    public MeetingResponseDTO registerAllExits(Long meetingId, String email) {
        Meeting meeting = access.findMeeting(meetingId);
        User president = access.findUser(email);
        access.requirePresident(president, meeting.getCommittee());
        LocalDateTime now = LocalDateTime.now();
        requireInProgress(meeting, now);

        List<MeetingParticipant> inside = participantRepo.findByMeetingIdOrderByUserNameAsc(meetingId).stream()
                .filter(p -> p.getEnteredAt() != null && p.getLeftAt() == null).toList();
        for (MeetingParticipant p : inside) {
            p.setLeftAt(now);
            p.setLeftBy(president);
        }
        participantRepo.saveAll(inside);
        if (!inside.isEmpty()) access.invalidateList(meeting);

        access.audit(president, "MEETING_EXITS_REGISTERED_BULK", meeting, " | Pessoas: " + inside.size());
        return access.detail(meeting, president, now);
    }

    /** Correction by the president: clears entry and exit of one person. */
    @Transactional
    public MeetingResponseDTO resetAttendance(Long meetingId, Long targetUserId, String email) {
        Meeting meeting = access.findMeeting(meetingId);
        User president = access.findUser(email);
        access.requirePresident(president, meeting.getCommittee());

        LocalDateTime now = LocalDateTime.now();
        MeetingStatus status = meeting.statusAt(now);
        if (status == MeetingStatus.CANCELED || status == MeetingStatus.SCHEDULED) {
            throw new IllegalStateException("Esta reunião não tem registros de presença para corrigir.");
        }
        MeetingParticipant participant = participantRepo.findForUpdate(meetingId, targetUserId)
                .orElseThrow(() -> new IllegalStateException("Este usuário não foi convidado para a reunião."));
        participant.setEnteredAt(null);
        participant.setEnteredBy(null);
        participant.setLeftAt(null);
        participant.setLeftBy(null);
        participantRepo.save(participant);
        access.invalidateList(meeting);

        access.audit(president, "MEETING_ATTENDANCE_RESET", meeting, " | Usuário ID: " + targetUserId);
        return access.detail(meeting, president, now);
    }
}
