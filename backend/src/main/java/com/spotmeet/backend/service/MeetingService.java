package com.spotmeet.backend.service;

import com.spotmeet.backend.dto.MeetingRequestDTO;
import com.spotmeet.backend.dto.MeetingResponseDTO;
import com.spotmeet.backend.model.*;
import com.spotmeet.backend.model.CommitteeMember.CommitteeMembershipStatus;
import com.spotmeet.backend.model.Meeting.MeetingPriority;
import com.spotmeet.backend.model.Meeting.MeetingStatus;
import com.spotmeet.backend.model.MeetingParticipant.InvitationResponse;
import com.spotmeet.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Scheduling of meetings: create, edit, cancel and read.
 *
 * Rules:
 *   - Only the committee president schedules, edits and cancels.
 *   - The president chooses who is invited (all active members by default) and is always invited.
 *   - Active members and organization leaders can see the meeting.
 *   - A meeting can only be edited before it starts.
 */
@Service
public class MeetingService {

    @Autowired private MeetingRepository meetingRepo;
    @Autowired private MeetingParticipantRepository participantRepo;
    @Autowired private CommitteeMemberRepository committeeMemberRepo;
    @Autowired private MeetingAccess access;

    private void requireFutureStart(MeetingRequestDTO dto, LocalDateTime now) {
        if (!dto.getStartsAt().isAfter(now)) {
            throw new IllegalStateException("O início da reunião deve ser uma data futura.");
        }
    }

    // Selected users must be active members; the president is always part of the list.
    private Map<Long, User> resolveInvitees(Long committeeId, User president, List<Long> selected) {
        Map<Long, User> active = new LinkedHashMap<>();
        for (CommitteeMember member : committeeMemberRepo.findByCommitteeIdAndStatus(committeeId, CommitteeMembershipStatus.ACTIVE)) {
            active.put(member.getUser().getId(), member.getUser());
        }
        if (selected == null) return active;

        Map<Long, User> invitees = new LinkedHashMap<>();
        for (Long userId : new LinkedHashSet<>(selected)) {
            User user = active.get(userId);
            if (user == null) {
                throw new IllegalStateException("Só integrantes ativos da comissão podem ser convidados.");
            }
            invitees.put(userId, user);
        }
        invitees.put(president.getId(), president);
        return invitees;
    }

    private void applyFields(Meeting meeting, MeetingRequestDTO dto) {
        meeting.setTitle(dto.getTitle());
        meeting.setAgenda(access.clean(dto.getAgenda()));
        meeting.setLocation(access.clean(dto.getLocation()));
        meeting.setLink(access.clean(dto.getLink()));
        meeting.setPriority(dto.getPriority() != null ? dto.getPriority() : MeetingPriority.MEDIUM);
        meeting.setStartsAt(dto.getStartsAt());
        meeting.setEndsAt(dto.getEndsAt());
    }

    // 1. Schedule (president only)

    @Transactional
    public MeetingResponseDTO createMeeting(Long committeeId, String email, MeetingRequestDTO dto) {
        Committee committee = access.findCommittee(committeeId);
        User president = access.findUser(email);
        access.requirePresident(president, committee);

        if (!committee.getOrganization().isApproved()) {
            throw new IllegalStateException("Esta organização ainda não foi aprovada ou está bloqueada.");
        }
        LocalDateTime now = LocalDateTime.now();
        requireFutureStart(dto, now);
        Map<Long, User> invitees = resolveInvitees(committeeId, president, dto.getInvitedUserIds());

        Meeting meeting = new Meeting();
        meeting.setCommittee(committee);
        meeting.setCreatedBy(president);
        applyFields(meeting, dto);
        Meeting saved = meetingRepo.save(meeting);

        List<MeetingParticipant> participants = new ArrayList<>();
        for (User invitee : invitees.values()) {
            MeetingParticipant participant = new MeetingParticipant(saved, invitee);
            if (invitee.getId().equals(president.getId())) {
                participant.setResponse(InvitationResponse.ACCEPTED);
                participant.setRespondedAt(now);
            }
            participants.add(participant);
        }
        participantRepo.saveAll(participants);

        access.audit(president, "MEETING_CREATED", saved, " | Convidados: " + participants.size());
        return MeetingResponseDTO.from(saved, participants, president.getId(), true, now);
    }

    // 2. Read

    @Transactional(readOnly = true)
    public List<MeetingResponseDTO> listCommitteeMeetings(Long committeeId, String email) {
        Committee committee = access.findCommittee(committeeId);
        User user = access.findUser(email);
        access.requireViewer(user, committee);
        return access.summaries(meetingRepo.findByCommitteeIdOrderByStartsAtDesc(committeeId), user, LocalDateTime.now());
    }

    // Invitations of the user plus every meeting of the committees they preside:
    // a president designated after a meeting was scheduled is not invited to it yet.
    @Transactional(readOnly = true)
    public List<MeetingResponseDTO> listMyMeetings(String email) {
        User user = access.findUser(email);
        Map<Long, Meeting> meetings = new LinkedHashMap<>();
        participantRepo.findActiveByUser(user.getId(), CommitteeMembershipStatus.ACTIVE)
                .forEach(p -> meetings.put(p.getMeeting().getId(), p.getMeeting()));
        committeeMemberRepo.findByUserIdAndStatus(user.getId(), CommitteeMembershipStatus.ACTIVE).stream()
                .filter(m -> m.getRole() == CommitteeMember.CommitteeRole.COMMITTEE_ADMIN)
                .flatMap(m -> meetingRepo.findByCommitteeIdOrderByStartsAtDesc(m.getCommittee().getId()).stream())
                .forEach(m -> meetings.putIfAbsent(m.getId(), m));
        List<Meeting> ordered = meetings.values().stream()
                .sorted(Comparator.comparing(Meeting::getStartsAt).reversed()).toList();
        return access.summaries(ordered, user, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public MeetingResponseDTO getMeeting(Long meetingId, String email) {
        Meeting meeting = access.findMeeting(meetingId);
        User user = access.findUser(email);
        access.requireViewer(user, meeting.getCommittee());
        return access.detail(meeting, user, LocalDateTime.now());
    }

    // 3. Edit and cancel (president only)

    @Transactional
    public MeetingResponseDTO updateMeeting(Long meetingId, String email, MeetingRequestDTO dto) {
        Meeting meeting = access.findMeeting(meetingId);
        User president = access.findUser(email);
        access.requirePresident(president, meeting.getCommittee());

        LocalDateTime now = LocalDateTime.now();
        if (meeting.statusAt(now) != MeetingStatus.SCHEDULED) {
            throw new IllegalStateException("Só é possível editar reuniões agendadas que ainda não começaram.");
        }
        requireFutureStart(dto, now);
        Map<Long, User> invitees = dto.getInvitedUserIds() == null ? null
                : resolveInvitees(meeting.getCommittee().getId(), president, dto.getInvitedUserIds());

        applyFields(meeting, dto);
        meetingRepo.save(meeting);
        if (invitees != null) syncInvitees(meeting, invitees);

        access.audit(president, "MEETING_UPDATED", meeting, "");
        return access.detail(meeting, president, now);
    }

    // Keeps the answers of people who stay invited and drops or adds the others.
    private void syncInvitees(Meeting meeting, Map<Long, User> invitees) {
        List<MeetingParticipant> current = participantRepo.findByMeetingIdOrderByUserNameAsc(meeting.getId());
        List<MeetingParticipant> removed = current.stream()
                .filter(p -> !invitees.containsKey(p.getUser().getId())).toList();
        if (!removed.isEmpty()) participantRepo.deleteAll(removed);

        Set<Long> already = new HashSet<>();
        current.forEach(p -> already.add(p.getUser().getId()));
        List<MeetingParticipant> added = invitees.values().stream()
                .filter(u -> !already.contains(u.getId()))
                .map(u -> new MeetingParticipant(meeting, u)).toList();
        if (!added.isEmpty()) participantRepo.saveAll(added);
    }

    @Transactional
    public MeetingResponseDTO cancelMeeting(Long meetingId, String email) {
        Meeting meeting = access.findMeeting(meetingId);
        User president = access.findUser(email);
        access.requirePresident(president, meeting.getCommittee());

        LocalDateTime now = LocalDateTime.now();
        MeetingStatus status = meeting.statusAt(now);
        if (status == MeetingStatus.CANCELED) {
            throw new IllegalStateException("Esta reunião já foi cancelada.");
        }
        if (status == MeetingStatus.ENDED) {
            throw new IllegalStateException("Não é possível cancelar uma reunião que já terminou.");
        }
        meeting.setCanceledAt(now);
        meetingRepo.save(meeting);

        access.audit(president, "MEETING_CANCELED", meeting, "");
        return access.detail(meeting, president, now);
    }
}
