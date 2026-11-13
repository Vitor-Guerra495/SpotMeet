package com.spotmeet.backend;

import com.spotmeet.backend.dto.AttendanceListDTO;
import com.spotmeet.backend.dto.MeetingRequestDTO;
import com.spotmeet.backend.dto.MeetingResponseDTO;
import com.spotmeet.backend.dto.*;
import com.spotmeet.backend.dto.PollResponseDTO.PollResult;
import com.spotmeet.backend.model.MeetingMinutes.MinutesStatus;
import com.spotmeet.backend.model.PollVote.VoteChoice;
import com.spotmeet.backend.model.*;
import com.spotmeet.backend.model.CommitteeMember.CommitteeMembershipStatus;
import com.spotmeet.backend.model.CommitteeMember.CommitteeRole;
import com.spotmeet.backend.model.Meeting.MeetingStatus;
import com.spotmeet.backend.model.MeetingParticipant.InvitationResponse;
import com.spotmeet.backend.repository.*;
import com.spotmeet.backend.service.*;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.spotmeet.backend.dto.MeetingRecurrenceDTO;
import org.mockito.ArgumentCaptor;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Services are real; repositories are simulated in memory.
class MeetingRulesTests {
    private final Map<Long, Meeting> meetings = new HashMap<>();
    private final List<MeetingParticipant> participants = new ArrayList<>();
    private final Map<Long, CommitteeMember> memberships = new LinkedHashMap<>();
    private final Map<Long, User> users = new HashMap<>();
    private AuditLogRepository audit;
    private Committee committee;
    private MeetingService service;
    private MeetingInvitationService invitation;
    private MeetingAttendanceService attendance;
    private AttendanceListService lists;
    private CommitteeService committeeService;
    private User president, member, outsider, owner;
    private final Map<Long, MeetingSeries> series = new HashMap<>();
    private EmailService mail;
    private final Map<Long, MeetingMinutes> minutesStore = new HashMap<>();
    private final List<MinutesResponse> minutesAnswers = new ArrayList<>();
    private final List<MeetingPoll> polls = new ArrayList<>();
    private final List<PollVote> votes = new ArrayList<>();
    private MeetingMinutesService minutesService;
    private MeetingPollService pollService;
    private AttendanceReportService reportService;

    private User user(long id, String name) {
        User u = new User(); u.setId(id); u.setName(name); u.setEmail(name.toLowerCase() + "@example.com"); u.setRole("USER");
        users.put(id, u);
        return u;
    }

    private void join(User u, CommitteeRole role) {
        memberships.put(u.getId(), new CommitteeMember(u, committee, CommitteeMembershipStatus.ACTIVE, role, null));
    }

    @BeforeEach
    void setUp() {
        president = user(1, "Ana"); member = user(2, "Bruno"); outsider = user(3, "Caio"); owner = user(4, "Dora");
        Organization org = new Organization(); org.setId(1L); org.setName("Equipe"); org.setOwner(owner); org.setApproved(true);
        committee = new Committee("Eventos", null, org); committee.setId(10L);
        join(president, CommitteeRole.COMMITTEE_ADMIN);
        join(member, CommitteeRole.MEMBER);

        UserRepository userRepo = mock(UserRepository.class);
        when(userRepo.findByEmail(anyString())).thenAnswer(call -> users.values().stream()
            .filter(u -> u.getEmail().equals(call.getArgument(0))).findFirst());

        OrganizationRepository orgRepo = mock(OrganizationRepository.class);
        when(orgRepo.findById(1L)).thenReturn(Optional.of(org));
        when(orgRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(org));

        CommitteeRepository committeeRepo = mock(CommitteeRepository.class);
        when(committeeRepo.findById(10L)).thenReturn(Optional.of(committee));

        CommitteeMemberRepository memberRepo = mock(CommitteeMemberRepository.class);
        when(memberRepo.findByUserIdAndCommitteeId(anyLong(), eq(10L)))
            .thenAnswer(call -> Optional.ofNullable(memberships.get(call.<Long>getArgument(0))));
        when(memberRepo.findByCommitteeIdAndStatus(10L, CommitteeMembershipStatus.ACTIVE))
            .thenAnswer(call -> new ArrayList<>(memberships.values()));
        when(memberRepo.existsByUserIdAndCommitteeIdAndStatus(anyLong(), eq(10L), eq(CommitteeMembershipStatus.ACTIVE)))
            .thenAnswer(call -> memberships.containsKey(call.<Long>getArgument(0)));
        when(memberRepo.findByCommitteeIdAndRole(10L, CommitteeRole.COMMITTEE_ADMIN))
            .thenAnswer(call -> memberships.values().stream().filter(m -> m.getRole() == CommitteeRole.COMMITTEE_ADMIN).toList());
        when(memberRepo.save(any())).thenAnswer(call -> call.getArgument(0));
        when(memberRepo.findByUserIdAndStatus(anyLong(), eq(CommitteeMembershipStatus.ACTIVE)))
            .thenAnswer(call -> memberships.values().stream().filter(m -> m.getUser().getId().equals(call.<Long>getArgument(0))).toList());

        audit = mock(AuditLogRepository.class);
        committeeService = new CommitteeService();
        ReflectionTestUtils.setField(committeeService, "committeeRepo", committeeRepo);
        ReflectionTestUtils.setField(committeeService, "committeeMemberRepo", memberRepo);
        ReflectionTestUtils.setField(committeeService, "organizationRepo", orgRepo);
        ReflectionTestUtils.setField(committeeService, "organizationMemberRepo", mock(OrganizationMemberRepository.class));
        ReflectionTestUtils.setField(committeeService, "userRepo", userRepo);
        ReflectionTestUtils.setField(committeeService, "auditLogRepo", audit);

        MeetingRepository meetingRepo = mock(MeetingRepository.class);
        when(meetingRepo.findById(anyLong())).thenAnswer(call -> Optional.ofNullable(meetings.get(call.<Long>getArgument(0))));
        when(meetingRepo.save(any())).thenAnswer(call -> {
            Meeting m = call.getArgument(0);
            if (m.getId() == null) m.setId(100L + meetings.size());
            meetings.put(m.getId(), m);
            return m;
        });
        when(meetingRepo.findByCommitteeIdOrderByStartsAtDesc(10L)).thenAnswer(call -> new ArrayList<>(meetings.values()));
        when(meetingRepo.findBySeriesIdOrderByStartsAtAsc(anyLong())).thenAnswer(call -> meetings.values().stream()
            .filter(m -> m.getSeries() != null && m.getSeries().getId().equals(call.<Long>getArgument(0)))
            .sorted(Comparator.comparing(Meeting::getStartsAt)).toList());

        MeetingSeriesRepository seriesRepo = mock(MeetingSeriesRepository.class);
        when(seriesRepo.save(any())).thenAnswer(call -> {
            MeetingSeries row = call.getArgument(0);
            if (row.getId() == null) row.setId(500L + series.size());
            series.put(row.getId(), row);
            return row;
        });
        when(seriesRepo.findByFinishedFalse()).thenAnswer(call -> series.values().stream().filter(row -> !row.isFinished()).toList());

        MeetingParticipantRepository participantRepo = mock(MeetingParticipantRepository.class);
        when(participantRepo.saveAll(any())).thenAnswer(call -> {
            Iterable<MeetingParticipant> list = call.getArgument(0);
            list.forEach(p -> { if (!participants.contains(p)) participants.add(p); });
            return list;
        });
        when(participantRepo.save(any())).thenAnswer(call -> call.getArgument(0));
        doAnswer(call -> { participants.removeAll((Collection<?>) call.getArgument(0)); return null; })
            .when(participantRepo).deleteAll(anyCollection());
        when(participantRepo.findByMeetingIdOrderByUserNameAsc(anyLong())).thenAnswer(call -> participants.stream()
            .filter(p -> p.getMeeting().getId().equals(call.<Long>getArgument(0)))
            .sorted(Comparator.comparing(p -> p.getUser().getName())).toList());
        when(participantRepo.findByMeetingIdIn(any())).thenAnswer(call -> {
            Collection<Long> ids = call.getArgument(0);
            return participants.stream().filter(p -> ids.contains(p.getMeeting().getId())).toList();
        });
        when(participantRepo.findForUpdate(anyLong(), anyLong())).thenAnswer(call -> participants.stream()
            .filter(p -> p.getMeeting().getId().equals(call.<Long>getArgument(0)) && p.getUser().getId().equals(call.<Long>getArgument(1)))
            .findFirst());

        when(participantRepo.findByMeetingIdAndUserId(anyLong(), anyLong())).thenAnswer(call -> participants.stream()
            .filter(p -> p.getMeeting().getId().equals(call.<Long>getArgument(0)) && p.getUser().getId().equals(call.<Long>getArgument(1)))
            .findFirst());
        when(participantRepo.findEnteredByUser(anyLong())).thenAnswer(call -> participants.stream()
            .filter(p -> p.getUser().getId().equals(call.<Long>getArgument(0)) && p.getEnteredAt() != null)
            .sorted(Comparator.comparing((MeetingParticipant p) -> p.getMeeting().getStartsAt()).reversed()).toList());

        MeetingMinutesRepository minutesRepo = mock(MeetingMinutesRepository.class);
        when(minutesRepo.findByMeetingId(anyLong())).thenAnswer(call -> Optional.ofNullable(minutesStore.get(call.<Long>getArgument(0))));
        when(minutesRepo.findForUpdate(anyLong())).thenAnswer(call -> Optional.ofNullable(minutesStore.get(call.<Long>getArgument(0))));
        when(minutesRepo.findByMeetingIdIn(any())).thenAnswer(call -> {
            Collection<Long> ids = call.getArgument(0);
            return minutesStore.values().stream().filter(m -> ids.contains(m.getMeeting().getId())).toList();
        });
        when(minutesRepo.save(any())).thenAnswer(call -> {
            MeetingMinutes m = call.getArgument(0);
            if (m.getId() == null) m.setId(700L + minutesStore.size());
            minutesStore.put(m.getMeeting().getId(), m);
            return m;
        });
        MinutesResponseRepository answerRepo = mock(MinutesResponseRepository.class);
        when(answerRepo.findByMinutesIdOrderByRespondedAtAsc(anyLong())).thenAnswer(call -> minutesAnswers.stream()
            .filter(r -> r.getMinutes().getId().equals(call.<Long>getArgument(0))).toList());
        when(answerRepo.findByMinutesIdInAndUserId(any(), anyLong())).thenAnswer(call -> {
            Collection<Long> ids = call.getArgument(0);
            return minutesAnswers.stream().filter(r -> ids.contains(r.getMinutes().getId()) && r.getUser().getId().equals(call.<Long>getArgument(1))).toList();
        });
        when(answerRepo.existsByMinutesIdAndUserId(anyLong(), anyLong())).thenAnswer(call -> minutesAnswers.stream()
            .anyMatch(r -> r.getMinutes().getId().equals(call.<Long>getArgument(0)) && r.getUser().getId().equals(call.<Long>getArgument(1))));
        when(answerRepo.save(any())).thenAnswer(call -> { minutesAnswers.add(call.getArgument(0)); return call.getArgument(0); });
        doAnswer(call -> { minutesAnswers.removeIf(r -> r.getMinutes().getId().equals(call.<Long>getArgument(0))); return null; })
            .when(answerRepo).deleteByMinutesId(anyLong());

        MeetingPollRepository pollRepo = mock(MeetingPollRepository.class);
        when(pollRepo.findByMeetingIdOrderByOpenedAtAsc(anyLong())).thenAnswer(call -> polls.stream()
            .filter(p -> p.getMeeting().getId().equals(call.<Long>getArgument(0))).toList());
        when(pollRepo.findForUpdate(anyLong(), anyLong())).thenAnswer(call -> polls.stream()
            .filter(p -> p.getMeeting().getId().equals(call.<Long>getArgument(0)) && p.getId().equals(call.<Long>getArgument(1))).findFirst());
        when(pollRepo.save(any())).thenAnswer(call -> {
            MeetingPoll p = call.getArgument(0);
            if (p.getId() == null) { p.setId(900L + polls.size()); polls.add(p); }
            return p;
        });
        PollVoteRepository voteRepo = mock(PollVoteRepository.class);
        when(voteRepo.findByPollIdInOrderByVotedAtAsc(any())).thenAnswer(call -> {
            Collection<Long> ids = call.getArgument(0);
            return votes.stream().filter(v -> ids.contains(v.getPoll().getId())).toList();
        });
        when(voteRepo.existsByPollIdAndUserId(anyLong(), anyLong())).thenAnswer(call -> votes.stream()
            .anyMatch(v -> v.getPoll().getId().equals(call.<Long>getArgument(0)) && v.getUser().getId().equals(call.<Long>getArgument(1))));
        when(voteRepo.save(any())).thenAnswer(call -> { votes.add(call.getArgument(0)); return call.getArgument(0); });

        MeetingAccess access = new MeetingAccess();
        ReflectionTestUtils.setField(access, "minutesRepo", minutesRepo);
        ReflectionTestUtils.setField(access, "responseRepo", answerRepo);
        ReflectionTestUtils.setField(access, "meetingRepo", meetingRepo);
        ReflectionTestUtils.setField(access, "participantRepo", participantRepo);
        ReflectionTestUtils.setField(access, "committeeRepo", committeeRepo);
        ReflectionTestUtils.setField(access, "committeeMemberRepo", memberRepo);
        ReflectionTestUtils.setField(access, "userRepo", userRepo);
        ReflectionTestUtils.setField(access, "auditLogRepo", audit);
        ReflectionTestUtils.setField(access, "committeeService", committeeService);

        service = new MeetingService();
        ReflectionTestUtils.setField(service, "meetingRepo", meetingRepo);
        ReflectionTestUtils.setField(service, "participantRepo", participantRepo);
        ReflectionTestUtils.setField(service, "committeeMemberRepo", memberRepo);
        ReflectionTestUtils.setField(service, "access", access);
        ReflectionTestUtils.setField(service, "seriesRepo", seriesRepo);
        ReflectionTestUtils.setField(service, "fileService", mock(MeetingFileService.class));
        // Dora owns the organization without taking part in the committee.
        OrganizationMemberRepository orgMembers = mock(OrganizationMemberRepository.class);
        OrganizationMember ownership = new OrganizationMember();
        ownership.setUser(owner); ownership.setOrganization(org); ownership.setRole(OrganizationMember.OrganizationRole.ORG_OWNER);
        when(orgMembers.findByUserId(owner.getId())).thenReturn(List.of(ownership));
        when(committeeRepo.findByOrganizationId(1L)).thenReturn(List.of(committee));
        ReflectionTestUtils.setField(service, "organizationMemberRepo", orgMembers);
        ReflectionTestUtils.setField(service, "committeeRepo", committeeRepo);
        mail = mock(EmailService.class);
        ReflectionTestUtils.setField(service, "emailService", mail);
        invitation = new MeetingInvitationService();
        ReflectionTestUtils.setField(invitation, "participantRepo", participantRepo);
        ReflectionTestUtils.setField(invitation, "access", access);
        ReflectionTestUtils.setField(invitation, "emailService", mail);
        attendance = new MeetingAttendanceService();
        ReflectionTestUtils.setField(attendance, "participantRepo", participantRepo);
        ReflectionTestUtils.setField(attendance, "access", access);
        ReflectionTestUtils.setField(attendance, "minutesRepo", minutesRepo);
        lists = new AttendanceListService();
        ReflectionTestUtils.setField(lists, "meetingRepo", meetingRepo);
        ReflectionTestUtils.setField(lists, "participantRepo", participantRepo);
        ReflectionTestUtils.setField(lists, "access", access);
        minutesService = new MeetingMinutesService();
        ReflectionTestUtils.setField(minutesService, "minutesRepo", minutesRepo);
        ReflectionTestUtils.setField(minutesService, "responseRepo", answerRepo);
        ReflectionTestUtils.setField(minutesService, "participantRepo", participantRepo);
        ReflectionTestUtils.setField(minutesService, "emailService", mail);
        ReflectionTestUtils.setField(minutesService, "access", access);
        pollService = new MeetingPollService();
        ReflectionTestUtils.setField(pollService, "pollRepo", pollRepo);
        ReflectionTestUtils.setField(pollService, "voteRepo", voteRepo);
        ReflectionTestUtils.setField(pollService, "participantRepo", participantRepo);
        ReflectionTestUtils.setField(pollService, "access", access);
        reportService = new AttendanceReportService();
        ReflectionTestUtils.setField(reportService, "participantRepo", participantRepo);
        ReflectionTestUtils.setField(reportService, "access", access);
    }

    private MeetingResponseDTO answer(Long meetingId, String email, boolean accept) {
        return invitation.respond(meetingId, email, accept, null);
    }

    private MeetingRequestDTO request(LocalDateTime start, LocalDateTime end) {
        MeetingRequestDTO dto = new MeetingRequestDTO();
        dto.setTitle("Planejamento   semanal"); dto.setAgenda("  Pauta 1  "); dto.setLocation("");
        dto.setStartsAt(start); dto.setEndsAt(end);
        return dto;
    }

    private MeetingResponseDTO schedule() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        return service.createMeeting(10L, president.getEmail(), request(start, start.plusHours(2)));
    }

    // Moves the meeting to the clock window so the in-progress rules can be tested.
    private Meeting startNow(MeetingResponseDTO created) {
        Meeting meeting = meetings.get(created.getId());
        meeting.setStartsAt(LocalDateTime.now().minusMinutes(30));
        meeting.setEndsAt(LocalDateTime.now().plusHours(1));
        return meeting;
    }

    @Test
    void presidentSchedulesAndInvitesAllActiveMembers() {
        MeetingResponseDTO created = schedule();
        assertEquals("Planejamento semanal", created.getTitle());
        assertEquals("Pauta 1", created.getAgenda());
        assertNull(created.getLocation());
        assertEquals(MeetingStatus.SCHEDULED, created.getStatus());
        assertEquals(2, created.getTotalInvited());
        assertEquals(1, created.getTotalAccepted());
        assertEquals(1, created.getTotalPending());
        assertEquals(InvitationResponse.ACCEPTED, created.getMyResponse());
        verify(audit).save(argThat(log -> "MEETING_CREATED".equals(log.getAction())));
    }

    @Test
    void onlyThePresidentSchedules() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        assertThrows(SecurityException.class, () -> service.createMeeting(10L, member.getEmail(), request(start, start.plusHours(1))));
        assertThrows(SecurityException.class, () -> service.createMeeting(10L, owner.getEmail(), request(start, start.plusHours(1))));
        assertThrows(SecurityException.class, () -> service.createMeeting(10L, outsider.getEmail(), request(start, start.plusHours(1))));
    }

    @Test
    void startMustBeInTheFuture() {
        LocalDateTime past = LocalDateTime.now().minusMinutes(1);
        assertThrows(IllegalStateException.class, () -> service.createMeeting(10L, president.getEmail(), request(past, past.plusHours(1))));
    }

    @Test
    void requestValidationRejectsBadTimes() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        assertTrue(validator.validate(request(start, start.plusHours(2))).isEmpty());
        assertFalse(validator.validate(request(start, start)).isEmpty());
        assertFalse(validator.validate(request(start, start.minusHours(1))).isEmpty());
        assertTrue(validator.validate(request(start, start.plusHours(24))).isEmpty());
        assertTrue(validator.validate(request(start, start.plusDays(3))).isEmpty());
        MeetingRequestDTO noTitle = request(start, start.plusHours(1)); noTitle.setTitle("  ");
        assertFalse(validator.validate(noTitle).isEmpty());
        MeetingRequestDTO longAgenda = request(start, start.plusHours(1)); longAgenda.setAgenda("a".repeat(2001));
        assertFalse(validator.validate(longAgenda).isEmpty());
        MeetingRequestDTO noDates = request(null, null);
        assertEquals(2, validator.validate(noDates).size());
    }

    @Test
    void visibilityIsLimitedToMembersAndOrganizationLeaders() {
        MeetingResponseDTO created = schedule();
        assertEquals(created.getId(), service.getMeeting(created.getId(), member.getEmail()).getId());
        assertEquals(created.getId(), service.getMeeting(created.getId(), owner.getEmail()).getId());
        assertThrows(SecurityException.class, () -> service.getMeeting(created.getId(), outsider.getEmail()));
        assertThrows(SecurityException.class, () -> service.listCommitteeMeetings(10L, outsider.getEmail()));
        assertEquals(1, service.listCommitteeMeetings(10L, member.getEmail()).size());
        assertNull(service.listCommitteeMeetings(10L, member.getEmail()).get(0).getParticipants());
    }

    @Test
    void aNewPresidentSeesTheMeetingsScheduledBeforeTheDesignation() {
        MeetingResponseDTO created = schedule();
        join(outsider, CommitteeRole.MEMBER);
        assertTrue(service.listMyMeetings(outsider.getEmail()).isEmpty());

        memberships.get(president.getId()).setRole(CommitteeRole.MEMBER);
        memberships.get(outsider.getId()).setRole(CommitteeRole.COMMITTEE_ADMIN);
        List<MeetingResponseDTO> mine = service.listMyMeetings(outsider.getEmail());
        assertEquals(1, mine.size());
        assertEquals(created.getId(), mine.get(0).getId());
        assertNull(mine.get(0).getMyResponse());
        assertTrue(service.listMyMeetings(member.getEmail()).isEmpty());
    }

    @Test
    void memberAnswersAndChangesTheAnswerUntilTheEnd() {
        MeetingResponseDTO created = schedule();
        assertEquals(InvitationResponse.DECLINED, answer(created.getId(), member.getEmail(), false).getMyResponse());
        MeetingResponseDTO accepted = answer(created.getId(), member.getEmail(), true);
        assertEquals(InvitationResponse.ACCEPTED, accepted.getMyResponse());
        assertEquals(2, accepted.getTotalAccepted());
        assertThrows(SecurityException.class, () -> answer(created.getId(), outsider.getEmail(), true));
    }

    @Test
    void invitedOnlyAtCreation() {
        MeetingResponseDTO created = schedule();
        User late = user(5, "Eva");
        join(late, CommitteeRole.MEMBER);
        assertThrows(IllegalStateException.class, () -> answer(created.getId(), late.getEmail(), true));
    }

    @Test
    void canceledOrEndedMeetingRefusesAnswers() {
        MeetingResponseDTO created = schedule();
        service.cancelMeeting(created.getId(), president.getEmail());
        assertThrows(IllegalStateException.class, () -> answer(created.getId(), member.getEmail(), true));
        assertThrows(IllegalStateException.class, () -> service.cancelMeeting(created.getId(), president.getEmail()));
        assertThrows(IllegalStateException.class, () -> service.updateMeeting(created.getId(), president.getEmail(),
            request(LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(1))));
    }

    @Test
    void thePresidentEditsScheduledAndInProgressMeetingsButNotEndedOnes() {
        MeetingResponseDTO created = schedule();
        LocalDateTime newStart = LocalDateTime.now().plusDays(3);
        MeetingRequestDTO edit = request(newStart, newStart.plusHours(1));
        assertThrows(SecurityException.class, () -> service.updateMeeting(created.getId(), member.getEmail(), edit));
        assertEquals(newStart, service.updateMeeting(created.getId(), president.getEmail(), edit).getStartsAt());

        // In progress: the start stays in the past and only the end moves.
        Meeting meeting = startNow(created);
        LocalDateTime start = meeting.getStartsAt();
        MeetingRequestDTO longer = request(start, LocalDateTime.now().plusHours(3));
        longer.setTitle("Planejamento estendido");
        MeetingResponseDTO extended = service.updateMeeting(created.getId(), president.getEmail(), longer);
        assertEquals(MeetingStatus.IN_PROGRESS, extended.getStatus());
        assertEquals("Planejamento estendido", extended.getTitle());
        MeetingRequestDTO earlier = request(start.minusHours(1), LocalDateTime.now().plusHours(3));
        assertThrows(IllegalStateException.class, () -> service.updateMeeting(created.getId(), president.getEmail(), earlier));
        MeetingRequestDTO finished = request(start, LocalDateTime.now().minusMinutes(1));
        assertThrows(IllegalStateException.class, () -> service.updateMeeting(created.getId(), president.getEmail(), finished));

        meeting.setStartsAt(LocalDateTime.now().minusHours(3));
        meeting.setEndsAt(LocalDateTime.now().minusHours(1));
        assertThrows(IllegalStateException.class, () -> service.updateMeeting(created.getId(), president.getEmail(), edit));
        assertThrows(SecurityException.class, () -> service.cancelMeeting(created.getId(), member.getEmail()));
    }

    @Test
    void entryAndExitFollowTheMeetingWindow() {
        MeetingResponseDTO created = schedule();
        Long id = created.getId();
        assertThrows(IllegalStateException.class, () -> attendance.registerEntry(id, member.getId(), member.getEmail()));
        Meeting meeting = startNow(created);

        assertThrows(IllegalStateException.class, () -> attendance.registerExit(id, member.getId(), member.getEmail()));
        attendance.registerEntry(id, member.getId(), member.getEmail());
        assertThrows(IllegalStateException.class, () -> attendance.registerEntry(id, member.getId(), member.getEmail()));
        attendance.registerExit(id, member.getId(), member.getEmail());
        assertThrows(IllegalStateException.class, () -> attendance.registerExit(id, member.getId(), member.getEmail()));

        meeting.setStartsAt(LocalDateTime.now().minusHours(3));
        meeting.setEndsAt(LocalDateTime.now().minusHours(1));
        assertThrows(IllegalStateException.class, () -> attendance.registerEntry(id, president.getId(), president.getEmail()));
    }

    @Test
    void onlyThePresidentRegistersForSomeoneElse() {
        MeetingResponseDTO created = schedule();
        startNow(created);
        Long id = created.getId();
        assertThrows(SecurityException.class, () -> attendance.registerEntry(id, president.getId(), member.getEmail()));
        assertThrows(SecurityException.class, () -> attendance.registerEntry(id, member.getId(), outsider.getEmail()));
        assertThrows(SecurityException.class, () -> attendance.registerEntry(id, member.getId(), owner.getEmail()));
        attendance.registerEntry(id, member.getId(), president.getEmail());
        assertThrows(IllegalStateException.class, () -> attendance.registerEntry(id, outsider.getId(), president.getEmail()));
    }

    @Test
    void presidentCorrectsAttendance() {
        MeetingResponseDTO created = schedule();
        startNow(created);
        Long id = created.getId();
        attendance.registerEntry(id, member.getId(), member.getEmail());
        assertThrows(SecurityException.class, () -> attendance.resetAttendance(id, member.getId(), member.getEmail()));
        attendance.resetAttendance(id, member.getId(), president.getEmail());
        attendance.registerEntry(id, member.getId(), member.getEmail());
    }

    @Test
    void attendanceListIsGeneratedByThePresidentAndReadByViewers() {
        MeetingResponseDTO created = schedule();
        Long id = created.getId();
        assertThrows(IllegalStateException.class, () -> lists.generate(id, president.getEmail()));
        Meeting meeting = startNow(created);
        attendance.registerEntry(id, president.getId(), president.getEmail());
        attendance.registerEntry(id, member.getId(), member.getEmail());
        attendance.registerExit(id, member.getId(), member.getEmail());

        assertThrows(IllegalStateException.class, () -> lists.get(id, member.getEmail()));
        assertThrows(SecurityException.class, () -> lists.generate(id, member.getEmail()));
        AttendanceListDTO list = lists.generate(id, president.getEmail());
        assertEquals(2, list.getTotalInvited());
        assertEquals(2, list.getTotalPresent());
        assertEquals(0, list.getTotalAbsent());
        assertEquals("Ana", list.getRows().get(0).getUserName());
        assertEquals(AttendanceListDTO.Situation.PRESENT, list.getRows().get(0).getSituation());
        assertNotNull(list.getRows().get(1).getDurationMinutes());
        assertEquals(2, lists.get(id, member.getEmail()).getRows().size());
        assertEquals(2, lists.get(id, owner.getEmail()).getRows().size());
        assertThrows(SecurityException.class, () -> lists.get(id, outsider.getEmail()));

        attendance.resetAttendance(id, member.getId(), president.getEmail());
        assertThrows(IllegalStateException.class, () -> lists.get(id, member.getEmail()));
        AttendanceListDTO regenerated = lists.generate(id, president.getEmail());
        assertEquals(1, regenerated.getTotalPresent());
        assertEquals(1, regenerated.getTotalAbsent());
        assertEquals(AttendanceListDTO.Situation.ABSENT, regenerated.getRows().get(1).getSituation());

        meeting.setStartsAt(LocalDateTime.now().minusHours(3));
        meeting.setEndsAt(LocalDateTime.now().minusHours(1));
        AttendanceListDTO ended = lists.generate(id, president.getEmail());
        assertEquals(AttendanceListDTO.Situation.EXIT_NOT_RECORDED, ended.getRows().get(0).getSituation());
        assertEquals(1, ended.getTotalPresent());
    }

    @Test
    void canceledMeetingHasNoAttendanceList() {
        MeetingResponseDTO created = schedule();
        startNow(created);
        service.cancelMeeting(created.getId(), president.getEmail());
        assertThrows(IllegalStateException.class, () -> lists.generate(created.getId(), president.getEmail()));
    }

    @Test
    void ownerDesignatesAndReplacesThePresident() {
        assertThrows(SecurityException.class, () -> committeeService.assignPresident(10L, member.getId(), member.getEmail()));
        assertThrows(SecurityException.class, () -> committeeService.assignPresident(10L, member.getId(), president.getEmail()));
        assertThrows(IllegalStateException.class, () -> committeeService.assignPresident(10L, outsider.getId(), owner.getEmail()));

        committeeService.assignPresident(10L, member.getId(), owner.getEmail());
        assertEquals(CommitteeRole.COMMITTEE_ADMIN, memberships.get(member.getId()).getRole());
        assertEquals(CommitteeRole.MEMBER, memberships.get(president.getId()).getRole());
        verify(audit).save(argThat(log -> "COMMITTEE_PRESIDENT_ASSIGNED".equals(log.getAction())));

        LocalDateTime start = LocalDateTime.now().plusDays(1);
        assertThrows(SecurityException.class, () -> service.createMeeting(10L, president.getEmail(), request(start, start.plusHours(1))));
        assertNotNull(service.createMeeting(10L, member.getEmail(), request(start, start.plusHours(1))).getId());
    }

    @Test
    void activeMembersListIsRestrictedToMembersAndLeaders() {
        assertEquals(2, committeeService.listActiveMembers(10L, member.getEmail()).size());
        assertEquals(2, committeeService.listActiveMembers(10L, owner.getEmail()).size());
        assertThrows(SecurityException.class, () -> committeeService.listActiveMembers(10L, outsider.getEmail()));
    }

    @Test
    void presidedCommitteesListsOnlyTheCommitteesOfThePresident() {
        assertEquals(1, committeeService.listPresidedCommittees(president.getEmail()).size());
        assertEquals(0, committeeService.listPresidedCommittees(member.getEmail()).size());
        assertEquals(0, committeeService.listPresidedCommittees(outsider.getEmail()).size());
    }

    @Test
    void presidentChoosesWhoIsInvitedAndCanChangeItBeforeTheStart() {
        User late = user(5, "Eva");
        join(late, CommitteeRole.MEMBER);
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        MeetingRequestDTO dto = request(start, start.plusHours(1));
        dto.setInvitedUserIds(List.of(member.getId()));
        MeetingResponseDTO created = service.createMeeting(10L, president.getEmail(), dto);
        assertEquals(2, created.getTotalInvited());

        MeetingRequestDTO outsiderDto = request(start, start.plusHours(1));
        outsiderDto.setInvitedUserIds(List.of(outsider.getId()));
        assertThrows(IllegalStateException.class, () -> service.createMeeting(10L, president.getEmail(), outsiderDto));

        MeetingRequestDTO edit = request(start, start.plusHours(1));
        edit.setInvitedUserIds(List.of(late.getId()));
        MeetingResponseDTO edited = service.updateMeeting(created.getId(), president.getEmail(), edit);
        assertEquals(List.of("Ana", "Eva"), edited.getParticipants().stream().map(MeetingResponseDTO.ParticipantDTO::getUserName).toList());

        MeetingRequestDTO keep = request(start, start.plusHours(1));
        assertEquals(2, service.updateMeeting(created.getId(), president.getEmail(), keep).getTotalInvited());
    }

    @Test
    void priorityDefaultsToMediumAndLinkAcceptsWebAddressesWithoutTheScheme() {
        MeetingResponseDTO created = schedule();
        assertEquals(Meeting.MeetingPriority.MEDIUM, created.getPriority());
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        MeetingRequestDTO high = request(start, start.plusHours(1));
        high.setPriority(Meeting.MeetingPriority.HIGH);
        high.setLink("  https://meet.example.com/sala  ");
        MeetingResponseDTO saved = service.createMeeting(10L, president.getEmail(), high);
        assertEquals(Meeting.MeetingPriority.HIGH, saved.getPriority());

        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        for (String valid : new String[] {"https://meet.example.com/x?a=1", "http://exemplo.com", "", "youtube.com", "www.meet.example.com/sala"}) {
            MeetingRequestDTO ok = request(start, start.plusHours(1)); ok.setLink(valid);
            assertTrue(validator.validate(ok).isEmpty(), valid);
        }
        MeetingRequestDTO bare = request(start, start.plusHours(1)); bare.setLink("  youtube.com/live  ");
        assertEquals("https://youtube.com/live", bare.getLink());
        for (String invalid : new String[] {"javascript:alert(1)", "ftp://exemplo.com", "localhost", "https://a b", "a".repeat(300) + ".com"}) {
            MeetingRequestDTO bad = request(start, start.plusHours(1)); bad.setLink(invalid);
            assertFalse(validator.validate(bad).isEmpty(), invalid);
        }
    }

    @Test
    void declineKeepsTheCommentAndAcceptClearsIt() {
        MeetingResponseDTO created = schedule();
        MeetingResponseDTO declined = invitation.respond(created.getId(), member.getEmail(), false, "  Não é da minha área  ");
        assertEquals("Não é da minha área", declined.getParticipants().get(1).getResponseComment());
        MeetingResponseDTO accepted = invitation.respond(created.getId(), member.getEmail(), true, "ignorado");
        assertNull(accepted.getParticipants().get(1).getResponseComment());
        MeetingResponseDTO blank = invitation.respond(created.getId(), member.getEmail(), false, "   ");
        assertNull(blank.getParticipants().get(1).getResponseComment());
    }

    @Test
    void eachNewRefusalEmailsThePresidentOnce() {
        MeetingResponseDTO created = schedule();
        invitation.respond(created.getId(), member.getEmail(), false, "Viagem marcada");
        verify(mail, times(1)).sendInvitationDeclinedEmail(eq(president.getEmail()), eq("Ana"), any(), eq("Bruno"), eq("Viagem marcada"));
        invitation.respond(created.getId(), member.getEmail(), false, "Viagem marcada");
        verify(mail, times(1)).sendInvitationDeclinedEmail(any(), any(), any(), eq("Bruno"), any());
        answer(created.getId(), member.getEmail(), true);
        answer(created.getId(), member.getEmail(), false);
        verify(mail, times(2)).sendInvitationDeclinedEmail(any(), any(), any(), eq("Bruno"), any());
        answer(created.getId(), president.getEmail(), false);
        verify(mail, never()).sendInvitationDeclinedEmail(any(), any(), any(), eq("Ana"), any());
    }

    @Test
    void onlyThePresidentReceivesTheRefusalsInTheList() {
        MeetingResponseDTO created = schedule();
        invitation.respond(created.getId(), member.getEmail(), false, "Viagem marcada");
        MeetingResponseDTO forPresident = service.listMyMeetings(president.getEmail()).get(0);
        assertEquals(1, forPresident.getDeclines().size());
        assertEquals("Bruno", forPresident.getDeclines().get(0).getUserName());
        assertEquals("Viagem marcada", forPresident.getDeclines().get(0).getResponseComment());
        assertNull(service.getMeeting(created.getId(), member.getEmail()).getDeclines());
        answer(created.getId(), member.getEmail(), true);
        assertTrue(service.listMyMeetings(president.getEmail()).get(0).getDeclines().isEmpty());
    }

    @Test
    void organizationLeadersFollowTheMeetingsWithoutAnInvitation() {
        MeetingResponseDTO created = schedule();
        List<MeetingResponseDTO> forOwner = service.listMyMeetings(owner.getEmail());
        assertEquals(1, forOwner.size());
        assertEquals(created.getId(), forOwner.get(0).getId());
        assertNull(forOwner.get(0).getMyResponse());
        assertNull(forOwner.get(0).getDeclines());
        assertTrue(service.listMyMeetings(outsider.getEmail()).isEmpty());
        assertThrows(SecurityException.class, () -> answer(created.getId(), owner.getEmail(), true));
    }

    @Test
    void recordsKeepWhoMadeThemAndTheBulkActionsSkipTheRightPeople() {
        User late = user(5, "Eva");
        join(late, CommitteeRole.MEMBER);
        MeetingResponseDTO created = schedule();
        Long id = created.getId();
        startNow(created);
        answer(id, member.getEmail(), true);
        answer(id, late.getEmail(), false);

        assertThrows(SecurityException.class, () -> attendance.registerAllEntries(id, member.getEmail()));
        attendance.registerEntry(id, member.getId(), member.getEmail());
        MeetingResponseDTO afterEntries = attendance.registerAllEntries(id, president.getEmail());
        MeetingResponseDTO.ParticipantDTO ana = afterEntries.getParticipants().get(0);
        MeetingResponseDTO.ParticipantDTO bruno = afterEntries.getParticipants().get(1);
        MeetingResponseDTO.ParticipantDTO eva = afterEntries.getParticipants().get(2);
        assertEquals("Ana", ana.getEnteredByName());
        assertEquals("Bruno", bruno.getEnteredByName());
        assertNull(eva.getEnteredAt());

        attendance.registerExit(id, member.getId(), member.getEmail());
        MeetingResponseDTO afterExits = attendance.registerAllExits(id, president.getEmail());
        assertEquals("Bruno", afterExits.getParticipants().get(1).getLeftByName());
        assertEquals("Ana", afterExits.getParticipants().get(0).getLeftByName());
        assertNull(afterExits.getParticipants().get(2).getLeftAt());
        assertThrows(SecurityException.class, () -> attendance.registerAllExits(id, member.getEmail()));

        attendance.resetAttendance(id, member.getId(), president.getEmail());
        MeetingResponseDTO cleared = service.getMeeting(id, president.getEmail());
        assertNull(cleared.getParticipants().get(1).getEnteredByName());
        assertNull(cleared.getParticipants().get(1).getLeftByName());
    }

    @Test
    void bulkActionsNeedAMeetingInProgress() {
        MeetingResponseDTO created = schedule();
        assertThrows(IllegalStateException.class, () -> attendance.registerAllEntries(created.getId(), president.getEmail()));
        assertThrows(IllegalStateException.class, () -> attendance.registerAllExits(created.getId(), president.getEmail()));
    }

    private MeetingRecurrenceDTO rule(MeetingSeries.Frequency frequency) {
        MeetingRecurrenceDTO rule = new MeetingRecurrenceDTO();
        rule.setFrequency(frequency);
        return rule;
    }

    @Test
    void allDayMeetingCoversWholeDaysAndCanStartToday() {
        LocalDateTime today = LocalDateTime.now().withHour(9);
        MeetingRequestDTO dto = request(today, today.plusDays(2).withHour(8));
        dto.setAllDay(true);
        MeetingResponseDTO created = service.createMeeting(10L, president.getEmail(), dto);
        assertTrue(created.isAllDay());
        assertEquals(today.toLocalDate().atStartOfDay(), created.getStartsAt());
        assertEquals(today.toLocalDate().plusDays(2).atTime(23, 59, 59), created.getEndsAt());
        assertEquals(MeetingStatus.IN_PROGRESS, created.getStatus());

        MeetingRequestDTO yesterday = request(today.minusDays(1), today.minusDays(1));
        yesterday.setAllDay(true);
        assertThrows(IllegalStateException.class, () -> service.createMeeting(10L, president.getEmail(), yesterday));
    }

    @Test
    void modalityKeepsOnlyThePlaceOrTheLinkItUses() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        MeetingRequestDTO online = request(start, start.plusHours(1));
        online.setLocation("Sala 3"); online.setLink("meet.example.com/x"); online.setModality(Meeting.MeetingModality.ONLINE);
        MeetingResponseDTO saved = service.createMeeting(10L, president.getEmail(), online);
        assertNull(saved.getLocation());
        assertEquals("https://meet.example.com/x", saved.getLink());

        MeetingRequestDTO both = request(start, start.plusHours(1));
        both.setLocation("Sala 3"); both.setLink("meet.example.com/x");
        assertEquals(Meeting.MeetingModality.HYBRID, service.createMeeting(10L, president.getEmail(), both).getModality());
    }

    @Test
    void allDaySeriesDatesEndAtTheLastSecondOfTheDay() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        MeetingRequestDTO dto = request(start, start.plusHours(1));
        dto.setAllDay(true);
        dto.setRecurrence(rule(MeetingSeries.Frequency.WEEKLY));
        service.createMeeting(10L, president.getEmail(), dto);
        assertFalse(meetings.isEmpty());
        for (Meeting date : meetings.values()) {
            assertEquals(date.getStartsAt().toLocalDate().atTime(23, 59, 59), date.getEndsAt());
        }
    }

    @Test
    void weeklySeriesCreatesTheNextThreeMonthsAndSendsOneInvitation() {
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(16).withMinute(0).withSecond(0).withNano(0);
        MeetingRequestDTO dto = request(start, start.plusHours(1));
        dto.setRecurrence(rule(MeetingSeries.Frequency.WEEKLY));
        MeetingResponseDTO first = service.createMeeting(10L, president.getEmail(), dto);

        assertNotNull(first.getSeriesId());
        assertEquals(start, first.getStartsAt());
        List<Meeting> dates = meetings.values().stream().sorted(Comparator.comparing(Meeting::getStartsAt)).toList();
        assertTrue(dates.size() >= 12 && dates.size() <= 15, "datas: " + dates.size());
        for (int i = 1; i < dates.size(); i++) {
            assertEquals(dates.get(i - 1).getStartsAt().plusWeeks(1), dates.get(i).getStartsAt());
            assertEquals(Duration.ofHours(1), Duration.between(dates.get(i).getStartsAt(), dates.get(i).getEndsAt()));
        }
        assertTrue(dates.get(dates.size() - 1).getStartsAt().toLocalDate().isBefore(LocalDate.now().plusMonths(3).plusDays(1)));
        assertEquals(dates.size() * 2L, participants.size());
        verify(mail, times(1)).sendMeetingInvitationEmail(eq(member.getEmail()), eq("Bruno"), any(), eq("Ana"));
        verify(mail, never()).sendMeetingInvitationEmail(eq(president.getEmail()), any(), any(), any());

        // Later runs add only the dates that entered the window.
        int before = meetings.size();
        service.extendSeries();
        assertEquals(before, meetings.size());
    }

    @Test
    void seriesEndsAfterTheChosenNumberOfTimesAndRejectsOverlaps() {
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        MeetingRequestDTO dto = request(start, start.plusHours(1));
        MeetingRecurrenceDTO daily = rule(MeetingSeries.Frequency.DAILY);
        daily.setOccurrences(4);
        dto.setRecurrence(daily);
        service.createMeeting(10L, president.getEmail(), dto);
        assertEquals(4, meetings.size());
        assertTrue(series.values().iterator().next().isFinished());

        MeetingRequestDTO long3Days = request(start, start.plusDays(3));
        long3Days.setRecurrence(rule(MeetingSeries.Frequency.DAILY));
        assertThrows(IllegalStateException.class, () -> service.createMeeting(10L, president.getEmail(), long3Days));

        MeetingRequestDTO both = request(start, start.plusHours(1));
        MeetingRecurrenceDTO twoEnds = rule(MeetingSeries.Frequency.DAILY);
        twoEnds.setOccurrences(2); twoEnds.setEndDate(start.toLocalDate().plusDays(5));
        both.setRecurrence(twoEnds);
        assertThrows(IllegalStateException.class, () -> service.createMeeting(10L, president.getEmail(), both));
    }

    @Test
    void cancelKeepsTheReasonAndEmailsTheGuests() {
        MeetingResponseDTO created = schedule();
        MeetingResponseDTO canceled = service.cancelMeeting(created.getId(), president.getEmail(), "  Sala indisponível  ");
        assertEquals(MeetingStatus.CANCELED, canceled.getStatus());
        assertEquals("Sala indisponível", canceled.getCancelReason());
        verify(mail).sendMeetingCanceledEmail(eq(member.getEmail()), eq("Bruno"), any(), eq("Ana"), eq("Sala indisponível"));
        verify(mail, never()).sendMeetingCanceledEmail(eq(president.getEmail()), any(), any(), any(), any());
    }

    @Test
    void editingEmailsWhatChangedAndInvitesOnlyTheNewGuests() {
        User late = user(5, "Eva");
        join(late, CommitteeRole.MEMBER);
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(16).withMinute(0).withSecond(0).withNano(0);
        MeetingRequestDTO dto = request(start, start.plusHours(1));
        dto.setInvitedUserIds(List.of(member.getId()));
        MeetingResponseDTO created = service.createMeeting(10L, president.getEmail(), dto);

        MeetingRequestDTO edit = request(start.plusHours(1), start.plusHours(2));
        edit.setLocation("Sala 5");
        edit.setInvitedUserIds(List.of(member.getId(), late.getId()));
        service.updateMeeting(created.getId(), president.getEmail(), edit);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String[]>> changes = ArgumentCaptor.forClass(List.class);
        verify(mail).sendMeetingChangedEmail(eq(member.getEmail()), eq("Bruno"), any(), eq("Ana"), changes.capture());
        assertEquals(List.of("Horário", "Local"), changes.getValue().stream().map(row -> row[0]).toList());
        verify(mail).sendMeetingInvitationEmail(eq(late.getEmail()), eq("Eva"), any(), eq("Ana"));
        verify(mail, never()).sendMeetingChangedEmail(eq(late.getEmail()), any(), any(), any(), any());

        clearInvocations(mail);
        service.updateMeeting(created.getId(), president.getEmail(), edit);
        verify(mail, never()).sendMeetingChangedEmail(any(), any(), any(), any(), any());
    }

    @Test
    void aGuestWhoAlreadyEnteredCannotBeRemovedFromTheMeeting() {
        MeetingResponseDTO created = schedule();
        Meeting meeting = startNow(created);
        attendance.registerEntry(created.getId(), member.getId(), member.getEmail());
        MeetingRequestDTO edit = request(meeting.getStartsAt(), meeting.getEndsAt());
        edit.setInvitedUserIds(List.of());
        assertThrows(IllegalStateException.class, () -> service.updateMeeting(created.getId(), president.getEmail(), edit));
    }

    // Incremento 3: minutes, votes and individual report.

    private Meeting endNow(MeetingResponseDTO created) {
        Meeting meeting = meetings.get(created.getId());
        meeting.setStartsAt(LocalDateTime.now().minusHours(3));
        meeting.setEndsAt(LocalDateTime.now().minusHours(1));
        return meeting;
    }

    private MeetingParticipant enter(Long meetingId, User user) {
        MeetingParticipant row = participants.stream()
            .filter(p -> p.getMeeting().getId().equals(meetingId) && p.getUser().getId().equals(user.getId())).findFirst().orElseThrow();
        row.setEnteredAt(LocalDateTime.now().minusHours(2));
        return row;
    }

    private MinutesRequestDTO text(String topics, String decisions) {
        MinutesRequestDTO dto = new MinutesRequestDTO();
        dto.setTopics(topics); dto.setDecisions(decisions);
        return dto;
    }

    private MinutesAnswerDTO reply(boolean approved, String reason) {
        MinutesAnswerDTO dto = new MinutesAnswerDTO();
        dto.setApproved(approved); dto.setReason(reason);
        return dto;
    }

    private PollRequestDTO question(String text) {
        PollRequestDTO dto = new PollRequestDTO();
        dto.setQuestion(text);
        return dto;
    }

    private PollVoteDTO vote(VoteChoice choice, String justification) {
        PollVoteDTO dto = new PollVoteDTO();
        dto.setChoice(choice); dto.setJustification(justification);
        return dto;
    }

    @Test
    void minutesAreWrittenAfterTheMeetingAndApprovedByThePresentPeople() {
        join(outsider, CommitteeRole.MEMBER);
        MeetingResponseDTO created = schedule();
        Long id = created.getId();
        startNow(created);
        assertThrows(IllegalStateException.class, () -> minutesService.saveDraft(id, president.getEmail(), text("Contas", null)));
        endNow(created);
        assertThrows(SecurityException.class, () -> minutesService.saveDraft(id, member.getEmail(), text("Contas", null)));
        enter(id, president);
        assertEquals(MinutesStatus.DRAFT, minutesService.saveDraft(id, president.getEmail(), text("Contas", null)).getStatus());
        assertNull(minutesService.get(id, member.getEmail()).getTopics());
        IllegalStateException alone = assertThrows(IllegalStateException.class,
            () -> minutesService.submit(id, president.getEmail(), text("Contas", "Aprovar")));
        assertEquals("Não dá para enviar: ninguém além de você registrou entrada nesta reunião.", alone.getMessage());

        enter(id, member); enter(id, outsider);
        assertThrows(IllegalStateException.class, () -> minutesService.submit(id, president.getEmail(), text("Contas", " ")));
        MinutesResponseDTO sent = minutesService.submit(id, president.getEmail(), text("Contas", "Aprovar"));
        assertEquals(MinutesStatus.IN_APPROVAL, sent.getStatus());
        assertEquals(2, sent.getTotalPending());
        verify(mail, times(2)).sendMinutesForApprovalEmail(anyString(), anyString(), any(), eq("Ana"), any());
        assertTrue(service.getMeeting(id, member.getEmail()).isMinutesToAnswer());

        assertThrows(IllegalStateException.class, () -> attendance.resetAttendance(id, member.getId(), president.getEmail()));
        assertThrows(IllegalStateException.class, () -> minutesService.answer(id, president.getEmail(), reply(true, null)));
        assertThrows(IllegalStateException.class, () -> minutesService.answer(id, member.getEmail(), reply(false, " ")));
        minutesService.answer(id, member.getEmail(), reply(true, null));
        minutesService.answer(id, outsider.getEmail(), reply(false, "Falta a sala."));
        assertThrows(IllegalStateException.class, () -> minutesService.answer(id, member.getEmail(), reply(false, "Mudei.")));
        assertFalse(service.getMeeting(id, member.getEmail()).isMinutesToAnswer());

        MinutesResponseDTO tie = minutesService.close(id, president.getEmail());
        assertEquals(MinutesStatus.REJECTED, tie.getStatus());
        assertEquals("Falta a sala.", tie.getAnswers().stream().filter(a -> Boolean.FALSE.equals(a.approved())).findFirst().orElseThrow().reason());
        assertThrows(IllegalStateException.class, () -> minutesService.saveDraft(id, president.getEmail(), text("x", "y")));

        MinutesResponseDTO again = minutesService.submit(id, president.getEmail(), text("Contas", "Aprovar e reservar a sala"));
        assertEquals(0, again.getTotalApproved() + again.getTotalRejected());
        minutesService.answer(id, member.getEmail(), reply(true, null));
        assertEquals(MinutesStatus.APPROVED, minutesService.close(id, president.getEmail()).getStatus());
        verify(mail, times(3)).sendMinutesApprovedEmail(anyString(), anyString(), any(), eq("1 aprovou, 0 reprovaram, 1 sem resposta."), any());
        assertThrows(IllegalStateException.class, () -> minutesService.submit(id, president.getEmail(), text("a", "b")));
    }

    @Test
    void votesAreForPresentPeopleAndTheResultAppearsAfterClosing() {
        join(outsider, CommitteeRole.MEMBER);
        MeetingResponseDTO created = schedule();
        Long id = created.getId();
        assertThrows(IllegalStateException.class, () -> pollService.open(id, president.getEmail(), question("Antes?")));
        Meeting meeting = startNow(created);
        assertThrows(SecurityException.class, () -> pollService.open(id, member.getEmail(), question("Pode?")));
        Long first = pollService.open(id, president.getEmail(), question("Aprovar o orçamento?")).get(0).getId();
        assertThrows(IllegalStateException.class, () -> pollService.vote(id, first, member.getEmail(), vote(VoteChoice.YES, null)));

        enter(id, president); enter(id, member); enter(id, outsider);
        pollService.vote(id, first, member.getEmail(), vote(VoteChoice.YES, "não vale"));
        assertThrows(IllegalStateException.class, () -> pollService.vote(id, first, member.getEmail(), vote(VoteChoice.NO, null)));
        PollResponseDTO live = pollService.vote(id, first, outsider.getEmail(), vote(VoteChoice.ABSTAIN, " Sem dados. ")).get(0);
        assertNull(live.getYes());
        assertEquals("Sem dados.", live.getMyVote().justification());
        PollResponseDTO forPresident = pollService.list(id, president.getEmail()).get(0);
        assertEquals(1, forPresident.getYes());
        assertEquals(1, forPresident.getNotVoted().size());
        assertNull(forPresident.getVotes().get(0).justification());
        assertEquals(PollResult.APPROVED, pollService.close(id, first, president.getEmail()).get(0).getResult());
        assertThrows(IllegalStateException.class, () -> pollService.vote(id, first, president.getEmail(), vote(VoteChoice.NO, null)));

        Long second = pollService.open(id, president.getEmail(), question("Trocar o horário?")).get(1).getId();
        pollService.vote(id, second, member.getEmail(), vote(VoteChoice.YES, null));
        pollService.vote(id, second, outsider.getEmail(), vote(VoteChoice.NO, null));
        pollService.open(id, president.getEmail(), question("Sem ninguém?"));
        meeting.setEndsAt(LocalDateTime.now().minusMinutes(1));
        List<PollResponseDTO> after = pollService.list(id, member.getEmail());
        assertTrue(after.get(1).isClosedAutomatically());
        assertEquals(PollResult.TIE, after.get(1).getResult());
        assertEquals(PollResult.NO_VOTES, after.get(2).getResult());
        assertThrows(IllegalStateException.class, () -> pollService.open(id, president.getEmail(), question("Tarde?")));
    }

    @Test
    void reportShowsOnlyTheMeetingsWhereTheUserEntered() {
        MeetingResponseDTO first = schedule();
        MeetingResponseDTO second = schedule();
        endNow(first); endNow(second);
        MeetingParticipant withExit = enter(first.getId(), member);
        withExit.setLeftAt(withExit.getEnteredAt().plusMinutes(45));
        enter(second.getId(), member);

        AttendanceReportDTO report = reportService.report(member.getEmail(), null, null, null);
        assertEquals(2, report.getTotalMeetings());
        assertEquals(45, report.getTotalMinutes());
        assertTrue(report.isMissingExit());
        assertEquals(1, report.getTotalCommittees());
        assertEquals(0, reportService.report(president.getEmail(), null, null, null).getTotalMeetings());
        LocalDate today = LocalDate.now();
        assertEquals(0, reportService.report(member.getEmail(), today.plusDays(1), today.plusDays(2), null).getTotalMeetings());
        assertEquals(0, reportService.report(member.getEmail(), null, null, 99L).getTotalMeetings());
        assertThrows(IllegalStateException.class, () -> reportService.report(member.getEmail(), today, today.minusDays(1), null));
    }
}
