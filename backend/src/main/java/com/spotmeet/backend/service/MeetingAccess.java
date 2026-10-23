package com.spotmeet.backend.service;

import com.spotmeet.backend.dto.MeetingResponseDTO;
import com.spotmeet.backend.model.*;
import com.spotmeet.backend.model.CommitteeMember.CommitteeMembershipStatus;
import com.spotmeet.backend.model.CommitteeMember.CommitteeRole;
import com.spotmeet.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Lookups, authorization checks and audit shared by the meeting services.
 *
 * Roles inside a meeting:
 *   - President: active committee member with the COMMITTEE_ADMIN role.
 *   - Viewer: active committee member or leader of the organization.
 */
@Component
public class MeetingAccess {

    @Autowired private MeetingRepository meetingRepo;
    @Autowired private MeetingParticipantRepository participantRepo;
    @Autowired private CommitteeRepository committeeRepo;
    @Autowired private CommitteeMemberRepository committeeMemberRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private AuditLogRepository auditLogRepo;
    @Autowired private CommitteeService committeeService;

    public User findUser(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));
    }

    public Committee findCommittee(Long committeeId) {
        return committeeRepo.findById(committeeId)
                .orElseThrow(() -> new IllegalArgumentException("Comissão não encontrada."));
    }

    public Meeting findMeeting(Long meetingId) {
        return meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Reunião não encontrada."));
    }

    public Optional<CommitteeMember> activeMembership(User user, Committee committee) {
        return committeeMemberRepo.findByUserIdAndCommitteeId(user.getId(), committee.getId())
                .filter(m -> m.getStatus() == CommitteeMembershipStatus.ACTIVE);
    }

    private boolean isOrganizationLeader(User user, Committee committee) {
        try {
            committeeService.requireOrganizationLeader(user, committee.getOrganization().getId());
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    public void requireViewer(User user, Committee committee) {
        if (activeMembership(user, committee).isEmpty() && !isOrganizationLeader(user, committee)) {
            throw new SecurityException("Acesso negado. Apenas integrantes da comissão ou líderes da organização veem esta reunião.");
        }
    }

    public void requireActiveMember(User user, Committee committee) {
        if (activeMembership(user, committee).isEmpty()) {
            throw new SecurityException("Acesso negado. Apenas integrantes ativos da comissão podem realizar esta operação.");
        }
    }

    public void requirePresident(User user, Committee committee) {
        boolean president = activeMembership(user, committee)
                .filter(m -> m.getRole() == CommitteeRole.COMMITTEE_ADMIN).isPresent();
        if (!president) {
            throw new SecurityException("Acesso negado. Apenas o presidente da comissão pode realizar esta operação.");
        }
    }

    public String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public void audit(User user, String action, Meeting meeting, String extra) {
        auditLogRepo.save(new AuditLog(user, action,
            "Reunião ID: " + meeting.getId() + " | Comissão: " + meeting.getCommittee().getName() + extra));
    }

    public MeetingResponseDTO detail(Meeting meeting, User viewer, LocalDateTime now) {
        List<MeetingParticipant> participants = participantRepo.findByMeetingIdOrderByUserNameAsc(meeting.getId());
        return MeetingResponseDTO.from(meeting, participants, viewer.getId(), true, now);
    }

    public List<MeetingResponseDTO> summaries(List<Meeting> meetings, User viewer, LocalDateTime now) {
        if (meetings.isEmpty()) return List.of();
        Map<Long, List<MeetingParticipant>> byMeeting = participantRepo
                .findByMeetingIdIn(meetings.stream().map(Meeting::getId).toList()).stream()
                .collect(Collectors.groupingBy(p -> p.getMeeting().getId()));
        return meetings.stream()
                .map(m -> MeetingResponseDTO.from(m, byMeeting.getOrDefault(m.getId(), List.of()), viewer.getId(), false, now))
                .toList();
    }

    // The list reflects the records, so it must be generated again after any change.
    public void invalidateList(Meeting meeting) {
        if (meeting.getAttendanceListGeneratedAt() != null) {
            meeting.setAttendanceListGeneratedAt(null);
            meeting.setAttendanceListGeneratedBy(null);
            meetingRepo.save(meeting);
        }
    }
}
