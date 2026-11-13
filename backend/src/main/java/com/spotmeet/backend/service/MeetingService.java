package com.spotmeet.backend.service;

import com.spotmeet.backend.dto.MeetingRecurrenceDTO;
import com.spotmeet.backend.dto.MeetingRequestDTO;
import com.spotmeet.backend.dto.MeetingResponseDTO;
import com.spotmeet.backend.model.*;
import com.spotmeet.backend.model.CommitteeMember.CommitteeMembershipStatus;
import com.spotmeet.backend.model.CommitteeMember.CommitteeRole;
import com.spotmeet.backend.model.Meeting.MeetingModality;
import com.spotmeet.backend.model.Meeting.MeetingPriority;
import com.spotmeet.backend.model.Meeting.MeetingStatus;
import com.spotmeet.backend.model.MeetingParticipant.InvitationResponse;
import com.spotmeet.backend.model.MeetingSeries.Frequency;
import com.spotmeet.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Scheduling of meetings: create, edit, cancel and read.
 *
 * Rules:
 *   - Only the committee president schedules, edits and cancels.
 *   - The president chooses who is invited (all active members by default) and is always invited.
 *   - Active members and organization leaders can see the meeting.
 *   - A meeting with hours starts in the future; an all-day meeting can start today.
 *   - A scheduled or in-progress meeting can be edited; the start only has to be in the future when it changes.
 *   - A repeated meeting creates one meeting per date for the next three months; the following dates
 *     are added over time. Editing or canceling changes only the chosen meeting.
 *   - Guests receive an e-mail when invited, when the meeting changes and when it is canceled.
 */
@Service
public class MeetingService {

    static final int SERIES_MONTHS = 3;
    private static final String OVERLAP = "A reunião dura mais que o intervalo entre as repetições. Encurte a reunião ou espace a frequência.";
    private static final String EMPTY = "Não informado";

    private record Range(LocalDateTime start, LocalDateTime end) {}

    @Autowired private MeetingRepository meetingRepo;
    @Autowired private MeetingParticipantRepository participantRepo;
    @Autowired private CommitteeMemberRepository committeeMemberRepo;
    @Autowired private OrganizationMemberRepository organizationMemberRepo;
    @Autowired private CommitteeRepository committeeRepo;
    @Autowired private MeetingSeriesRepository seriesRepo;
    @Autowired private MeetingFileService fileService;
    @Autowired private EmailService emailService;
    @Autowired private MeetingAccess access;

    // An all-day meeting keeps only the dates: 00:00 of the first day to 23:59:59 of the last one.
    private Range range(MeetingRequestDTO dto) {
        if (!dto.wholeDay()) return new Range(dto.getStartsAt(), dto.getEndsAt());
        return new Range(dto.getStartsAt().toLocalDate().atStartOfDay(), dto.getEndsAt().toLocalDate().atTime(LocalTime.of(23, 59, 59)));
    }

    private void requireFutureStart(MeetingRequestDTO dto, LocalDateTime now) {
        if (dto.wholeDay()) {
            if (dto.getStartsAt().toLocalDate().isBefore(now.toLocalDate())) {
                throw new IllegalStateException("A reunião de dia inteiro não pode começar antes de hoje.");
            }
        } else if (!dto.getStartsAt().isAfter(now)) {
            throw new IllegalStateException("O início da reunião deve ser uma data futura.");
        }
    }

    private Map<Long, User> activeMembers(Long committeeId) {
        Map<Long, User> active = new LinkedHashMap<>();
        for (CommitteeMember member : committeeMemberRepo.findByCommitteeIdAndStatus(committeeId, CommitteeMembershipStatus.ACTIVE)) {
            active.put(member.getUser().getId(), member.getUser());
        }
        return active;
    }

    // Selected users must be active members; the president is always part of the list.
    private Map<Long, User> resolveInvitees(Long committeeId, User president, List<Long> selected) {
        Map<Long, User> active = activeMembers(committeeId);
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

    // Without a modality (older app versions) it is deduced from the place and the link.
    private static MeetingModality modality(MeetingRequestDTO dto) {
        if (dto.getModality() != null) return dto.getModality();
        boolean place = dto.getLocation() != null && !dto.getLocation().isBlank();
        boolean online = dto.getLink() != null && !dto.getLink().isBlank();
        return place && online ? MeetingModality.HYBRID : online ? MeetingModality.ONLINE : MeetingModality.IN_PERSON;
    }

    private void applyFields(Meeting meeting, MeetingRequestDTO dto, Range range) {
        MeetingModality modality = modality(dto);
        meeting.setTitle(dto.getTitle());
        meeting.setAgenda(access.clean(dto.getAgenda()));
        meeting.setModality(modality);
        meeting.setLocation(modality == MeetingModality.ONLINE ? null : access.clean(dto.getLocation()));
        meeting.setLink(modality == MeetingModality.IN_PERSON ? null : access.clean(dto.getLink()));
        meeting.setPriority(dto.getPriority() != null ? dto.getPriority() : MeetingPriority.MEDIUM);
        meeting.setAllDay(dto.wholeDay());
        meeting.setStartsAt(range.start());
        meeting.setEndsAt(range.end());
    }

    private List<MeetingParticipant> invite(Meeting meeting, Collection<User> invitees, Set<Long> accepted, LocalDateTime now) {
        List<MeetingParticipant> participants = new ArrayList<>();
        for (User invitee : invitees) {
            MeetingParticipant participant = new MeetingParticipant(meeting, invitee);
            if (accepted.contains(invitee.getId())) {
                participant.setResponse(InvitationResponse.ACCEPTED);
                participant.setRespondedAt(now);
            }
            participants.add(participant);
        }
        participantRepo.saveAll(participants);
        return participants;
    }

    // Address of the page that opens the meeting in the app; null outside a request (scheduled tasks, tests).
    static String appLink(Long meetingId) {
        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath().path("/abrir/reuniao/{id}").buildAndExpand(meetingId).toUriString();
        } catch (IllegalStateException noRequest) {
            return null;
        }
    }

    private void emailInvitations(Meeting meeting, Collection<User> guests, User sender) {
        MeetingText.Mail mail = MeetingText.mail(meeting, appLink(meeting.getId()));
        for (User guest : guests) {
            if (!guest.getId().equals(sender.getId())) {
                emailService.sendMeetingInvitationEmail(guest.getEmail(), guest.getName(), mail, sender.getName());
            }
        }
    }

    // Schedule (president only)

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
        MeetingFile attachment = fileService.resolve(dto.getAttachmentFileId(), committee, president, null);

        if (dto.getRecurrence() != null) return createSeries(committee, president, dto, invitees, attachment, now);

        Meeting meeting = new Meeting();
        meeting.setCommittee(committee);
        meeting.setCreatedBy(president);
        applyFields(meeting, dto, range(dto));
        meeting.setAttachment(attachment);
        Meeting saved = meetingRepo.save(meeting);
        List<MeetingParticipant> participants = invite(saved, invitees.values(), Set.of(president.getId()), now);

        access.audit(president, "MEETING_CREATED", saved, " | Convidados: " + participants.size());
        emailInvitations(saved, invitees.values(), president);
        return MeetingResponseDTO.from(saved, participants, president.getId(), true, now);
    }

    private MeetingResponseDTO createSeries(Committee committee, User president, MeetingRequestDTO dto, Map<Long, User> invitees,
                                           MeetingFile attachment, LocalDateTime now) {
        Range range = range(dto);
        MeetingSeries series = new MeetingSeries();
        series.setCommittee(committee);
        series.setCreatedBy(president);
        applyRule(series, dto.getRecurrence(), range.start().toLocalDate());
        Meeting template = new Meeting();
        applyFields(template, dto, range);
        series.setTitle(template.getTitle());
        series.setAgenda(template.getAgenda());
        series.setLocation(template.getLocation());
        series.setLink(template.getLink());
        series.setModality(template.getModality());
        series.setPriority(template.getPriority());
        series.setAllDay(template.isAllDay());
        series.setAttachment(attachment);
        series.setFirstStartsAt(range.start());
        series.setDurationMinutes(Duration.between(range.start(), range.end()).toMinutes());
        series.setInvitedUserIds(new LinkedHashSet<>(invitees.keySet()));
        MeetingSeries saved = seriesRepo.save(series);

        List<Meeting> created = extend(saved, now, true);
        if (created.isEmpty()) {
            throw new IllegalStateException("A frequência escolhida não tem nenhuma data. Revise os dias e o fim da repetição.");
        }
        Meeting first = created.get(0);
        access.audit(president, "MEETING_CREATED", first, " | Série: " + created.size() + " reuniões criadas");
        emailInvitations(first, invitees.values(), president);
        return access.detail(first, president, now);
    }

    private void applyRule(MeetingSeries series, MeetingRecurrenceDTO rule, LocalDate firstDay) {
        if (rule.getEndDate() != null && rule.getOccurrences() != null) {
            throw new IllegalStateException("Escolha só uma forma de terminar a repetição: em uma data ou após um número de vezes.");
        }
        if (rule.getEndDate() != null && rule.getEndDate().isBefore(firstDay)) {
            throw new IllegalStateException("A repetição precisa terminar depois do primeiro dia da reunião.");
        }
        series.setFrequency(rule.getFrequency());
        series.setRepeatInterval(rule.getInterval() == null ? 1 : rule.getInterval());
        Set<DayOfWeek> days = new TreeSet<>();
        if (rule.getFrequency() == Frequency.WEEKLY && rule.getWeekDays() != null) {
            rule.getWeekDays().forEach(day -> days.add(DayOfWeek.of(day)));
        }
        series.setWeekDays(days.isEmpty() ? null : MeetingRecurrence.weekDaysText(days));
        series.setMonthlyByWeekday(rule.getFrequency() == Frequency.MONTHLY && Boolean.TRUE.equals(rule.getMonthlyByWeekday()));
        series.setEndDate(rule.getEndDate());
        series.setMaxOccurrences(rule.getOccurrences());
    }

    /**
     * Creates the meetings of the series up to three months from now.
     * When scheduling (strict), overlapping dates are an error; later, they only stop the series.
     */
    private List<Meeting> extend(MeetingSeries series, LocalDateTime now, boolean strict) {
        LocalDate after = series.getLastStartsAt() == null ? null : series.getLastStartsAt().toLocalDate();
        MeetingRecurrence.Window window = MeetingRecurrence.window(series, after, now.toLocalDate().plusMonths(SERIES_MONTHS));
        LocalTime time = series.getFirstStartsAt().toLocalTime();
        LocalDateTime previousEnd = series.getLastStartsAt() == null ? null : series.getLastStartsAt().plusMinutes(series.getDurationMinutes());

        Map<Long, User> invitees = seriesInvitees(series);
        Set<Long> presidents = presidents(series.getCommittee().getId());
        List<Meeting> created = new ArrayList<>();
        for (LocalDate day : window.dates()) {
            LocalDateTime start = day.atTime(time);
            LocalDateTime end = start.plusMinutes(series.getDurationMinutes());
            // The duration is kept in whole minutes; an all-day date ends at 23:59:59, like a single all-day meeting.
            if (series.isAllDay()) end = end.withSecond(59);
            if (previousEnd != null && start.isBefore(previousEnd)) {
                if (strict) throw new IllegalStateException(OVERLAP);
                series.setFinished(true);
                break;
            }
            Meeting meeting = fromSeries(series, start, end);
            Meeting saved = meetingRepo.save(meeting);
            invite(saved, invitees.values(), presidents, now);
            created.add(saved);
            previousEnd = end;
            series.setLastStartsAt(start);
            series.setCreatedCount(series.getCreatedCount() + 1);
        }
        if (window.exhausted()) series.setFinished(true);
        seriesRepo.save(series);
        return created;
    }

    private static Meeting fromSeries(MeetingSeries series, LocalDateTime start, LocalDateTime end) {
        Meeting meeting = new Meeting();
        meeting.setCommittee(series.getCommittee());
        meeting.setCreatedBy(series.getCreatedBy());
        meeting.setSeries(series);
        meeting.setTitle(series.getTitle());
        meeting.setAgenda(series.getAgenda());
        meeting.setLocation(series.getLocation());
        meeting.setLink(series.getLink());
        meeting.setModality(series.getModality());
        meeting.setPriority(series.getPriority());
        meeting.setAllDay(series.isAllDay());
        meeting.setAttachment(series.getAttachment());
        meeting.setStartsAt(start);
        meeting.setEndsAt(end);
        return meeting;
    }

    // Guests chosen for the series who are still active members, plus the current president.
    private Map<Long, User> seriesInvitees(MeetingSeries series) {
        Map<Long, User> active = activeMembers(series.getCommittee().getId());
        Set<Long> presidents = presidents(series.getCommittee().getId());
        Map<Long, User> invitees = new LinkedHashMap<>();
        active.forEach((id, user) -> {
            if (series.getInvitedUserIds().contains(id) || presidents.contains(id)) invitees.put(id, user);
        });
        return invitees;
    }

    private Set<Long> presidents(Long committeeId) {
        Set<Long> ids = new HashSet<>();
        for (CommitteeMember member : committeeMemberRepo.findByCommitteeIdAndRole(committeeId, CommitteeRole.COMMITTEE_ADMIN)) {
            if (member.getStatus() == CommitteeMembershipStatus.ACTIVE) ids.add(member.getUser().getId());
        }
        return ids;
    }

    /** Adds the dates of the series that entered the three-month window. Runs one minute after start and every hour. */
    @Scheduled(initialDelay = 60_000, fixedDelay = 3_600_000)
    @Transactional
    public void extendSeries() {
        LocalDateTime now = LocalDateTime.now();
        for (MeetingSeries series : seriesRepo.findByFinishedFalse()) {
            extend(series, now, false);
        }
    }

    // Read

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
                .filter(m -> m.getRole() == CommitteeRole.COMMITTEE_ADMIN)
                .flatMap(m -> meetingRepo.findByCommitteeIdOrderByStartsAtDesc(m.getCommittee().getId()).stream())
                .forEach(m -> meetings.putIfAbsent(m.getId(), m));
        // Owner, vice and subowner also follow the meetings of every committee of the organization, only to read.
        organizationMemberRepo.findByUserId(user.getId()).stream()
                .filter(m -> m.getRole() != OrganizationMember.OrganizationRole.MEMBER)
                .flatMap(m -> committeeRepo.findByOrganizationId(m.getOrganization().getId()).stream())
                .flatMap(c -> meetingRepo.findByCommitteeIdOrderByStartsAtDesc(c.getId()).stream())
                .forEach(m -> meetings.putIfAbsent(m.getId(), m));
        List<Meeting> ordered = meetings.values().stream()
                .sorted(Comparator.comparing(Meeting::getStartsAt).reversed()).toList();
        return access.summaries(ordered, user, LocalDateTime.now());
    }

    /** Number shown on the Reuniões tab: invitations not answered of meetings that were not canceled and have not ended. */
    @Transactional(readOnly = true)
    public long countPendingInvitations(String email) {
        User user = access.findUser(email);
        return participantRepo.countPending(user.getId(), InvitationResponse.PENDING, LocalDateTime.now(), CommitteeMembershipStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public MeetingResponseDTO getMeeting(Long meetingId, String email) {
        Meeting meeting = access.findMeeting(meetingId);
        User user = access.findUser(email);
        access.requireViewer(user, meeting.getCommittee());
        return access.detail(meeting, user, LocalDateTime.now());
    }

    // Edit and cancel (president only)

    @Transactional
    public MeetingResponseDTO updateMeeting(Long meetingId, String email, MeetingRequestDTO dto) {
        Meeting meeting = access.findMeeting(meetingId);
        User president = access.findUser(email);
        access.requirePresident(president, meeting.getCommittee());

        LocalDateTime now = LocalDateTime.now();
        MeetingStatus status = meeting.statusAt(now);
        if (status != MeetingStatus.SCHEDULED && status != MeetingStatus.IN_PROGRESS) {
            throw new IllegalStateException("Só é possível editar reuniões agendadas ou em andamento.");
        }
        Range range = range(dto);
        if (!range.start().equals(meeting.getStartsAt())) requireFutureStart(dto, now);
        if (!range.end().isAfter(now)) {
            throw new IllegalStateException("O término precisa ser depois de agora.");
        }
        Map<Long, User> invitees = dto.getInvitedUserIds() == null ? null
                : resolveInvitees(meeting.getCommittee().getId(), president, dto.getInvitedUserIds());
        MeetingFile previousFile = meeting.getAttachment();
        MeetingFile attachment = fileService.resolve(dto.getAttachmentFileId(), meeting.getCommittee(), president, previousFile);
        MeetingText.Mail before = MeetingText.mail(meeting, null);

        applyFields(meeting, dto, range);
        meeting.setAttachment(attachment);
        meetingRepo.save(meeting);
        List<User> added = invitees == null ? List.of() : syncInvitees(meeting, invitees);
        if (previousFile != null && previousFile != attachment) fileService.deleteIfUnused(previousFile);
        access.invalidateList(meeting);

        access.audit(president, "MEETING_UPDATED", meeting, "");
        emailChanges(meeting, before, added, president);
        return access.detail(meeting, president, now);
    }

    // Keeps the answers of people who stay invited and drops or adds the others.
    private List<User> syncInvitees(Meeting meeting, Map<Long, User> invitees) {
        List<MeetingParticipant> current = participantRepo.findByMeetingIdOrderByUserNameAsc(meeting.getId());
        List<MeetingParticipant> removed = current.stream()
                .filter(p -> !invitees.containsKey(p.getUser().getId())).toList();
        if (removed.stream().anyMatch(p -> p.getEnteredAt() != null)) {
            throw new IllegalStateException("Não é possível retirar do convite quem já registrou entrada.");
        }
        if (!removed.isEmpty()) participantRepo.deleteAll(removed);

        Set<Long> already = new HashSet<>();
        current.forEach(p -> already.add(p.getUser().getId()));
        List<User> added = invitees.values().stream().filter(u -> !already.contains(u.getId())).toList();
        if (!added.isEmpty()) participantRepo.saveAll(added.stream().map(u -> new MeetingParticipant(meeting, u)).toList());
        return added;
    }

    // New guests get the invitation; the others get what changed, when something they see changed.
    private void emailChanges(Meeting meeting, MeetingText.Mail before, List<User> added, User president) {
        MeetingText.Mail after = MeetingText.mail(meeting, appLink(meeting.getId()));
        List<String[]> changes = new ArrayList<>();
        compare(changes, "Título", before.title(), after.title());
        compare(changes, "Data", before.date(), after.date());
        compare(changes, "Horário", before.time(), after.time());
        compare(changes, "Modalidade", before.modality(), after.modality());
        compare(changes, "Local", before.location(), after.location());
        compare(changes, "Link", before.link(), after.link());
        compare(changes, "Pauta", before.agenda(), after.agenda());
        compare(changes, "Anexo", before.attachment(), after.attachment());

        Set<Long> addedIds = new HashSet<>();
        added.forEach(user -> addedIds.add(user.getId()));
        for (User guest : added) {
            if (!guest.getId().equals(president.getId())) {
                emailService.sendMeetingInvitationEmail(guest.getEmail(), guest.getName(), after, president.getName());
            }
        }
        if (changes.isEmpty()) return;
        for (MeetingParticipant participant : participantRepo.findByMeetingIdOrderByUserNameAsc(meeting.getId())) {
            User guest = participant.getUser();
            if (guest.getId().equals(president.getId()) || addedIds.contains(guest.getId())) continue;
            emailService.sendMeetingChangedEmail(guest.getEmail(), guest.getName(), after, president.getName(), changes);
        }
    }

    private static void compare(List<String[]> changes, String label, String before, String after) {
        if (!Objects.equals(before, after)) {
            changes.add(new String[] {label, before == null ? EMPTY : before, after == null ? EMPTY : after});
        }
    }

    @Transactional
    public MeetingResponseDTO cancelMeeting(Long meetingId, String email) {
        return cancelMeeting(meetingId, email, null);
    }

    @Transactional
    public MeetingResponseDTO cancelMeeting(Long meetingId, String email, String reason) {
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
        meeting.setCancelReason(access.clean(reason));
        meetingRepo.save(meeting);

        access.audit(president, "MEETING_CANCELED", meeting, "");
        MeetingText.Mail mail = MeetingText.mail(meeting, appLink(meeting.getId()));
        for (MeetingParticipant participant : participantRepo.findByMeetingIdOrderByUserNameAsc(meeting.getId())) {
            User guest = participant.getUser();
            if (!guest.getId().equals(president.getId())) {
                emailService.sendMeetingCanceledEmail(guest.getEmail(), guest.getName(), mail, president.getName(), meeting.getCancelReason());
            }
        }
        return access.detail(meeting, president, now);
    }
}
