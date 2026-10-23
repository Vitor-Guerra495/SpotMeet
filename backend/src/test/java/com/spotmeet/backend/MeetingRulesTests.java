package com.spotmeet.backend;

import com.spotmeet.backend.dto.AttendanceListDTO;
import com.spotmeet.backend.dto.MeetingRequestDTO;
import com.spotmeet.backend.dto.MeetingResponseDTO;
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

        MeetingAccess access = new MeetingAccess();
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
        invitation = new MeetingInvitationService();
        ReflectionTestUtils.setField(invitation, "participantRepo", participantRepo);
        ReflectionTestUtils.setField(invitation, "access", access);
        attendance = new MeetingAttendanceService();
        ReflectionTestUtils.setField(attendance, "participantRepo", participantRepo);
        ReflectionTestUtils.setField(attendance, "access", access);
        lists = new AttendanceListService();
        ReflectionTestUtils.setField(lists, "meetingRepo", meetingRepo);
        ReflectionTestUtils.setField(lists, "participantRepo", participantRepo);
        ReflectionTestUtils.setField(lists, "access", access);
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
        assertFalse(validator.validate(request(start, start.plusHours(24).plusMinutes(1))).isEmpty());
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
    void onlyAScheduledMeetingCanBeEditedByThePresident() {
        MeetingResponseDTO created = schedule();
        LocalDateTime newStart = LocalDateTime.now().plusDays(3);
        MeetingRequestDTO edit = request(newStart, newStart.plusHours(1));
        assertThrows(SecurityException.class, () -> service.updateMeeting(created.getId(), member.getEmail(), edit));
        assertEquals(newStart, service.updateMeeting(created.getId(), president.getEmail(), edit).getStartsAt());
        startNow(created);
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
    void priorityDefaultsToMediumAndLinkAcceptsOnlyWebAddresses() {
        MeetingResponseDTO created = schedule();
        assertEquals(Meeting.MeetingPriority.MEDIUM, created.getPriority());
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        MeetingRequestDTO high = request(start, start.plusHours(1));
        high.setPriority(Meeting.MeetingPriority.HIGH);
        high.setLink("  https://meet.example.com/sala  ");
        MeetingResponseDTO saved = service.createMeeting(10L, president.getEmail(), high);
        assertEquals(Meeting.MeetingPriority.HIGH, saved.getPriority());

        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        for (String valid : new String[] {"https://meet.example.com/x?a=1", "http://exemplo.com", ""}) {
            MeetingRequestDTO ok = request(start, start.plusHours(1)); ok.setLink(valid);
            assertTrue(validator.validate(ok).isEmpty(), valid);
        }
        for (String invalid : new String[] {"javascript:alert(1)", "ftp://exemplo.com", "meet.example.com", "https://a b", "http://" + "a".repeat(300)}) {
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
}
