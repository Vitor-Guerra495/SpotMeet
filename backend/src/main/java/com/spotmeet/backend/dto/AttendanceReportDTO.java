package com.spotmeet.backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Individual report: meetings where the user registered an entry, in the chosen period and committee.
 * A meeting without exit has no time and does not count in totalMinutes.
 */
public class AttendanceReportDTO {

    public record Row(Long meetingId, String title, Long committeeId, String committeeName, LocalDateTime startsAt,
                      LocalDateTime enteredAt, LocalDateTime leftAt, Long durationMinutes) {}

    public record CommitteeOption(Long committeeId, String committeeName) {}

    private final LocalDate from;
    private final LocalDate to;
    private final Long committeeId;
    private final int totalMeetings;
    private final long totalMinutes;
    private final int totalCommittees;
    private final boolean missingExit;
    private final List<Row> rows;
    private final List<CommitteeOption> committees;

    public AttendanceReportDTO(LocalDate from, LocalDate to, Long committeeId, List<Row> rows, List<CommitteeOption> committees) {
        this.from = from;
        this.to = to;
        this.committeeId = committeeId;
        this.rows = rows;
        this.committees = committees;
        this.totalMeetings = rows.size();
        this.totalMinutes = rows.stream().filter(r -> r.durationMinutes() != null).mapToLong(Row::durationMinutes).sum();
        this.totalCommittees = (int) rows.stream().map(Row::committeeId).distinct().count();
        this.missingExit = rows.stream().anyMatch(r -> r.durationMinutes() == null);
    }

    public LocalDate getFrom() { return from; }
    public LocalDate getTo() { return to; }
    public Long getCommitteeId() { return committeeId; }
    public int getTotalMeetings() { return totalMeetings; }
    public long getTotalMinutes() { return totalMinutes; }
    public int getTotalCommittees() { return totalCommittees; }
    public boolean isMissingExit() { return missingExit; }
    public List<Row> getRows() { return rows; }
    public List<CommitteeOption> getCommittees() { return committees; }
}
