package com.spotmeet.backend.dto;

import com.spotmeet.backend.model.MeetingMinutes;
import com.spotmeet.backend.model.MeetingMinutes.MinutesStatus;
import com.spotmeet.backend.model.MeetingParticipant;
import com.spotmeet.backend.model.MinutesResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Minutes of a meeting as the app shows them.
 *
 * Present people are the ones with an entry record. The text of a draft and the answer of each person
 * are only sent to the president; everyone sees the totals and their own answer.
 */
public class MinutesResponseDTO {

    public record Person(Long userId, String userName) {}

    /** Answer of one present person; approved is null while the person has not answered. */
    public record Answer(Long userId, String userName, Boolean approved, String reason, LocalDateTime respondedAt) {}

    private Long meetingId;
    private MinutesStatus status;
    private String topics;
    private String decisions;
    private String notes;
    private String writtenByName;
    private LocalDateTime updatedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime closedAt;
    private List<Person> present;
    private List<Person> absent;
    private int totalApproved;
    private int totalRejected;
    private int totalPending;
    private boolean viewerPresent;
    private boolean canAnswer;
    private Answer myAnswer;
    private List<Answer> answers;

    public MinutesResponseDTO() {}

    /**
     * @param minutes      null when the president has not started the minutes
     * @param participants invitations of the meeting, ordered by name
     * @param responses    answers of the current round
     * @param president    true when the viewer presides the committee
     */
    public static MinutesResponseDTO from(Long meetingId, MeetingMinutes minutes, List<MeetingParticipant> participants,
                                          List<MinutesResponse> responses, Long viewerId, boolean president) {
        MinutesResponseDTO dto = new MinutesResponseDTO();
        dto.meetingId = meetingId;
        dto.present = participants.stream().filter(p -> p.getEnteredAt() != null)
                .map(p -> new Person(p.getUser().getId(), p.getUser().getName())).toList();
        dto.absent = participants.stream().filter(p -> p.getEnteredAt() == null)
                .map(p -> new Person(p.getUser().getId(), p.getUser().getName())).toList();
        dto.viewerPresent = dto.present.stream().anyMatch(p -> p.userId().equals(viewerId));
        if (minutes == null) return dto;

        dto.status = minutes.getStatus();
        dto.writtenByName = minutes.getWrittenBy().getName();
        dto.updatedAt = minutes.getUpdatedAt();
        dto.submittedAt = minutes.getSubmittedAt();
        dto.closedAt = minutes.getClosedAt();
        if (president || minutes.getStatus() != MinutesStatus.DRAFT) {
            dto.topics = minutes.getTopics();
            dto.decisions = minutes.getDecisions();
            dto.notes = minutes.getNotes();
        }
        if (minutes.getStatus() == MinutesStatus.DRAFT) return dto;

        // The writer does not answer their own minutes.
        Long writerId = minutes.getWrittenBy().getId();
        Map<Long, MinutesResponse> byUser = responses.stream()
                .collect(Collectors.toMap(r -> r.getUser().getId(), Function.identity(), (a, b) -> a));
        List<Answer> all = dto.present.stream().filter(p -> !p.userId().equals(writerId)).map(p -> {
            MinutesResponse r = byUser.get(p.userId());
            return r == null ? new Answer(p.userId(), p.userName(), null, null, null)
                : new Answer(p.userId(), p.userName(), r.isApproved(), r.getReason(), r.getRespondedAt());
        }).toList();
        dto.totalApproved = (int) all.stream().filter(a -> Boolean.TRUE.equals(a.approved())).count();
        dto.totalRejected = (int) all.stream().filter(a -> Boolean.FALSE.equals(a.approved())).count();
        dto.totalPending = all.size() - dto.totalApproved - dto.totalRejected;
        dto.myAnswer = all.stream().filter(a -> a.userId().equals(viewerId) && a.approved() != null).findFirst().orElse(null);
        dto.canAnswer = minutes.getStatus() == MinutesStatus.IN_APPROVAL && dto.myAnswer == null
                && all.stream().anyMatch(a -> a.userId().equals(viewerId));
        if (president) dto.answers = all;
        return dto;
    }

    public Long getMeetingId() { return meetingId; }
    public MinutesStatus getStatus() { return status; }
    public String getTopics() { return topics; }
    public String getDecisions() { return decisions; }
    public String getNotes() { return notes; }
    public String getWrittenByName() { return writtenByName; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public List<Person> getPresent() { return present; }
    public List<Person> getAbsent() { return absent; }
    public int getTotalApproved() { return totalApproved; }
    public int getTotalRejected() { return totalRejected; }
    public int getTotalPending() { return totalPending; }
    public boolean isViewerPresent() { return viewerPresent; }
    public boolean isCanAnswer() { return canAnswer; }
    public Answer getMyAnswer() { return myAnswer; }
    public List<Answer> getAnswers() { return answers; }
}
