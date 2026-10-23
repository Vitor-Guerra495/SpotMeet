package com.spotmeet.backend.dto;

import com.spotmeet.backend.model.Meeting;
import com.spotmeet.backend.model.MeetingParticipant;
import com.spotmeet.backend.model.MeetingParticipant.InvitationResponse;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Attendance list of a meeting. A person is present when the entry was recorded.
 */
public class AttendanceListDTO {

    public enum Situation {
        PRESENT,
        EXIT_NOT_RECORDED,
        ABSENT
    }

    public static class Row {
        private final Long userId;
        private final String userName;
        private final InvitationResponse response;
        private final String responseComment;
        private final LocalDateTime enteredAt;
        private final String enteredByName;
        private final LocalDateTime leftAt;
        private final String leftByName;
        private final Long durationMinutes;
        private final Situation situation;

        Row(MeetingParticipant p, boolean meetingEnded) {
            this.userId = p.getUser().getId();
            this.userName = p.getUser().getName();
            this.response = p.getResponse();
            this.responseComment = p.getResponseComment();
            this.enteredAt = p.getEnteredAt();
            this.enteredByName = p.getEnteredBy() != null ? p.getEnteredBy().getName() : null;
            this.leftAt = p.getLeftAt();
            this.leftByName = p.getLeftBy() != null ? p.getLeftBy().getName() : null;
            this.durationMinutes = p.getEnteredAt() != null && p.getLeftAt() != null
                ? Duration.between(p.getEnteredAt(), p.getLeftAt()).toMinutes() : null;
            if (p.getEnteredAt() == null) this.situation = Situation.ABSENT;
            else if (p.getLeftAt() == null && meetingEnded) this.situation = Situation.EXIT_NOT_RECORDED;
            else this.situation = Situation.PRESENT;
        }

        public Long getUserId() { return userId; }
        public String getUserName() { return userName; }
        public InvitationResponse getResponse() { return response; }
        public String getResponseComment() { return responseComment; }
        public LocalDateTime getEnteredAt() { return enteredAt; }
        public String getEnteredByName() { return enteredByName; }
        public LocalDateTime getLeftAt() { return leftAt; }
        public String getLeftByName() { return leftByName; }
        public Long getDurationMinutes() { return durationMinutes; }
        public Situation getSituation() { return situation; }
    }

    private Long meetingId;
    private String title;
    private String committeeName;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private LocalDateTime generatedAt;
    private String generatedByName;
    private int totalInvited;
    private int totalPresent;
    private int totalAbsent;
    private List<Row> rows;

    public AttendanceListDTO() {}

    public static AttendanceListDTO from(Meeting meeting, List<MeetingParticipant> participants, LocalDateTime now) {
        AttendanceListDTO dto = new AttendanceListDTO();
        boolean ended = now.isAfter(meeting.getEndsAt());
        dto.meetingId = meeting.getId();
        dto.title = meeting.getTitle();
        dto.committeeName = meeting.getCommittee().getName();
        dto.startsAt = meeting.getStartsAt();
        dto.endsAt = meeting.getEndsAt();
        dto.generatedAt = meeting.getAttendanceListGeneratedAt();
        dto.generatedByName = meeting.getAttendanceListGeneratedBy() != null
            ? meeting.getAttendanceListGeneratedBy().getName() : null;
        dto.rows = participants.stream().map(p -> new Row(p, ended)).toList();
        dto.totalInvited = dto.rows.size();
        dto.totalPresent = (int) dto.rows.stream().filter(r -> r.situation != Situation.ABSENT).count();
        dto.totalAbsent = dto.totalInvited - dto.totalPresent;
        return dto;
    }

    public Long getMeetingId() { return meetingId; }
    public String getTitle() { return title; }
    public String getCommitteeName() { return committeeName; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public String getGeneratedByName() { return generatedByName; }
    public int getTotalInvited() { return totalInvited; }
    public int getTotalPresent() { return totalPresent; }
    public int getTotalAbsent() { return totalAbsent; }
    public List<Row> getRows() { return rows; }
}
