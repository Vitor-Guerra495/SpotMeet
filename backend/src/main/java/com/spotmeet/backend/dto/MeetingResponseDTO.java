package com.spotmeet.backend.dto;

import com.spotmeet.backend.model.Meeting;
import com.spotmeet.backend.model.Meeting.MeetingPriority;
import com.spotmeet.backend.model.Meeting.MeetingStatus;
import com.spotmeet.backend.model.MeetingParticipant;
import com.spotmeet.backend.model.MeetingParticipant.InvitationResponse;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Meeting with invitation counters. The participant list is only filled in the detail view.
 */
public class MeetingResponseDTO {

    public static class ParticipantDTO {
        private final Long userId;
        private final String userName;
        private final InvitationResponse response;
        private final String responseComment;
        private final LocalDateTime respondedAt;
        private final LocalDateTime enteredAt;
        private final String enteredByName;
        private final LocalDateTime leftAt;
        private final String leftByName;

        public ParticipantDTO(MeetingParticipant p) {
            this.userId = p.getUser().getId();
            this.userName = p.getUser().getName();
            this.response = p.getResponse();
            this.responseComment = p.getResponseComment();
            this.respondedAt = p.getRespondedAt();
            this.enteredAt = p.getEnteredAt();
            this.enteredByName = p.getEnteredBy() != null ? p.getEnteredBy().getName() : null;
            this.leftAt = p.getLeftAt();
            this.leftByName = p.getLeftBy() != null ? p.getLeftBy().getName() : null;
        }

        public Long getUserId() { return userId; }
        public String getUserName() { return userName; }
        public InvitationResponse getResponse() { return response; }
        public String getResponseComment() { return responseComment; }
        public LocalDateTime getRespondedAt() { return respondedAt; }
        public LocalDateTime getEnteredAt() { return enteredAt; }
        public String getEnteredByName() { return enteredByName; }
        public LocalDateTime getLeftAt() { return leftAt; }
        public String getLeftByName() { return leftByName; }
    }

    private Long id;
    private Long committeeId;
    private String committeeName;
    private Long organizationId;
    private String title;
    private String agenda;
    private String location;
    private String link;
    private MeetingPriority priority;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private MeetingStatus status;
    private String createdByName;
    private LocalDateTime canceledAt;
    private LocalDateTime attendanceListGeneratedAt;
    private InvitationResponse myResponse;
    private int totalInvited;
    private int totalAccepted;
    private int totalDeclined;
    private int totalPending;
    private List<ParticipantDTO> participants;

    public MeetingResponseDTO() {}

    public static MeetingResponseDTO from(Meeting meeting, List<MeetingParticipant> participants,
                                          Long viewerId, boolean includeParticipants, LocalDateTime now) {
        MeetingResponseDTO dto = new MeetingResponseDTO();
        dto.id = meeting.getId();
        dto.committeeId = meeting.getCommittee().getId();
        dto.committeeName = meeting.getCommittee().getName();
        dto.organizationId = meeting.getCommittee().getOrganization().getId();
        dto.title = meeting.getTitle();
        dto.agenda = meeting.getAgenda();
        dto.location = meeting.getLocation();
        dto.link = meeting.getLink();
        dto.priority = meeting.getPriority();
        dto.startsAt = meeting.getStartsAt();
        dto.endsAt = meeting.getEndsAt();
        dto.status = meeting.statusAt(now);
        dto.createdByName = meeting.getCreatedBy().getName();
        dto.canceledAt = meeting.getCanceledAt();
        dto.attendanceListGeneratedAt = meeting.getAttendanceListGeneratedAt();
        for (MeetingParticipant p : participants) {
            dto.totalInvited++;
            if (p.getResponse() == InvitationResponse.ACCEPTED) dto.totalAccepted++;
            else if (p.getResponse() == InvitationResponse.DECLINED) dto.totalDeclined++;
            else dto.totalPending++;
            if (viewerId != null && p.getUser().getId().equals(viewerId)) dto.myResponse = p.getResponse();
        }
        if (includeParticipants) {
            dto.participants = participants.stream().map(ParticipantDTO::new).toList();
        }
        return dto;
    }

    public Long getId() { return id; }
    public Long getCommitteeId() { return committeeId; }
    public String getCommitteeName() { return committeeName; }
    public Long getOrganizationId() { return organizationId; }
    public String getTitle() { return title; }
    public String getAgenda() { return agenda; }
    public String getLocation() { return location; }
    public String getLink() { return link; }
    public MeetingPriority getPriority() { return priority; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public MeetingStatus getStatus() { return status; }
    public String getCreatedByName() { return createdByName; }
    public LocalDateTime getCanceledAt() { return canceledAt; }
    public LocalDateTime getAttendanceListGeneratedAt() { return attendanceListGeneratedAt; }
    public InvitationResponse getMyResponse() { return myResponse; }
    public int getTotalInvited() { return totalInvited; }
    public int getTotalAccepted() { return totalAccepted; }
    public int getTotalDeclined() { return totalDeclined; }
    public int getTotalPending() { return totalPending; }
    public List<ParticipantDTO> getParticipants() { return participants; }
}
