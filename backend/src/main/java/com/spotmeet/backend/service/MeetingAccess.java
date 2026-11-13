package com.spotmeet.backend.service;

import com.spotmeet.backend.dto.MeetingResponseDTO;
import com.spotmeet.backend.model.*;
import com.spotmeet.backend.model.CommitteeMember.CommitteeMembershipStatus;
import com.spotmeet.backend.model.CommitteeMember.CommitteeRole;
import com.spotmeet.backend.model.MeetingMinutes.MinutesStatus;
import com.spotmeet.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    @Autowired private MeetingMinutesRepository minutesRepo;
    @Autowired private MinutesResponseRepository responseRepo;

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

    /** Active president of the committee, if there is one. */
    public Optional<User> president(Committee committee) {
        return committeeMemberRepo.findByCommitteeIdAndRole(committee.getId(), CommitteeRole.COMMITTEE_ADMIN).stream()
                .filter(m -> m.getStatus() == CommitteeMembershipStatus.ACTIVE)
                .map(CommitteeMember::getUser).findFirst();
    }

    public boolean isPresident(User user, Committee committee) {
        return activeMembership(user, committee).filter(m -> m.getRole() == CommitteeRole.COMMITTEE_ADMIN).isPresent();
    }

    public void requirePresident(User user, Committee committee) {
        if (!isPresident(user, committee)) {
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
        return summarize(List.of(meeting), viewer, now, true).get(0);
    }

    public List<MeetingResponseDTO> summaries(List<Meeting> meetings, User viewer, LocalDateTime now) {
        return summarize(meetings, viewer, now, false);
    }

    private List<MeetingResponseDTO> summarize(List<Meeting> meetings, User viewer, LocalDateTime now, boolean includeParticipants) {
        if (meetings.isEmpty()) return List.of();
        List<Long> ids = meetings.stream().map(Meeting::getId).toList();
        Map<Long, List<MeetingParticipant>> byMeeting = participantRepo.findByMeetingIdIn(ids).stream()
                .collect(Collectors.groupingBy(p -> p.getMeeting().getId()));
        Map<Long, MeetingMinutes> minutes = minutesRepo.findByMeetingIdIn(ids).stream()
                .collect(Collectors.toMap(m -> m.getMeeting().getId(), m -> m, (a, b) -> a));
        List<Long> inApproval = minutes.values().stream()
                .filter(m -> m.getStatus() == MinutesStatus.IN_APPROVAL).map(MeetingMinutes::getId).toList();
        Set<Long> answered = inApproval.isEmpty() ? Set.of() : responseRepo.findByMinutesIdInAndUserId(inApproval, viewer.getId()).stream()
                .map(r -> r.getMinutes().getId()).collect(Collectors.toSet());
        Set<Long> presided = committeeMemberRepo.findByUserIdAndStatus(viewer.getId(), CommitteeMembershipStatus.ACTIVE).stream()
                .filter(m -> m.getRole() == CommitteeRole.COMMITTEE_ADMIN)
                .map(m -> m.getCommittee().getId()).collect(Collectors.toSet());

        List<MeetingResponseDTO> result = new ArrayList<>();
        for (Meeting m : meetings) {
            List<MeetingParticipant> participants = byMeeting.getOrDefault(m.getId(), List.of());
            if (includeParticipants) participants = participants.stream()
                    .sorted(Comparator.comparing((MeetingParticipant p) -> p.getUser().getName())).toList();
            MeetingResponseDTO dto = MeetingResponseDTO.from(m, participants, viewer.getId(), includeParticipants, now);
            MeetingMinutes mm = minutes.get(m.getId());
            boolean toAnswer = mm != null && mm.getStatus() == MinutesStatus.IN_APPROVAL && dto.getMyEnteredAt() != null
                    && !mm.getWrittenBy().getId().equals(viewer.getId()) && !answered.contains(mm.getId());
            if (presided.contains(m.getCommittee().getId())) {
                dto.withDeclines(participants.stream()
                        .filter(p -> p.getResponse() == MeetingParticipant.InvitationResponse.DECLINED)
                        .sorted(Comparator.comparing(MeetingParticipant::getRespondedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                        .toList());
            }
            result.add(dto.withMinutes(mm == null ? null : mm.getStatus(), toAnswer));
        }
        return result;
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
