package com.spotmeet.backend.service;

import com.spotmeet.backend.dto.AttendanceReportDTO;
import com.spotmeet.backend.dto.AttendanceReportDTO.CommitteeOption;
import com.spotmeet.backend.dto.AttendanceReportDTO.Row;
import com.spotmeet.backend.model.Meeting;
import com.spotmeet.backend.model.MeetingParticipant;
import com.spotmeet.backend.model.User;
import com.spotmeet.backend.repository.MeetingParticipantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Individual report of the authenticated user.
 *
 * Rules:
 *   - A meeting counts when the user registered an entry; the user always comes from the token.
 *   - Optional filters: period (start date of the meeting, both days included) and committee.
 *   - The time is the difference between entry and exit; without exit there is no time.
 */
@Service
public class AttendanceReportService {

    @Autowired private MeetingParticipantRepository participantRepo;
    @Autowired private MeetingAccess access;

    @Transactional(readOnly = true)
    public AttendanceReportDTO report(String email, LocalDate from, LocalDate to, Long committeeId) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalStateException("A data final não pode ser antes da data inicial.");
        }
        User user = access.findUser(email);
        List<MeetingParticipant> entered = participantRepo.findEnteredByUser(user.getId());

        // The committee filter lists every committee the user took part in, whatever the period.
        Map<Long, String> committees = new LinkedHashMap<>();
        entered.forEach(p -> committees.putIfAbsent(p.getMeeting().getCommittee().getId(), p.getMeeting().getCommittee().getName()));

        List<Row> rows = entered.stream().filter(p -> {
            Meeting m = p.getMeeting();
            LocalDate day = m.getStartsAt().toLocalDate();
            return (from == null || !day.isBefore(from)) && (to == null || !day.isAfter(to))
                && (committeeId == null || m.getCommittee().getId().equals(committeeId));
        }).map(p -> {
            Meeting m = p.getMeeting();
            Long minutes = p.getLeftAt() == null ? null : Duration.between(p.getEnteredAt(), p.getLeftAt()).toMinutes();
            return new Row(m.getId(), m.getTitle(), m.getCommittee().getId(), m.getCommittee().getName(), m.getStartsAt(),
                p.getEnteredAt(), p.getLeftAt(), minutes);
        }).toList();

        List<CommitteeOption> options = committees.entrySet().stream()
                .map(e -> new CommitteeOption(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(CommitteeOption::committeeName, String.CASE_INSENSITIVE_ORDER)).toList();
        return new AttendanceReportDTO(from, to, committeeId, rows, options);
    }
}
