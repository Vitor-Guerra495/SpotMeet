package com.spotmeet.backend.model;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Vote opened by the president during a meeting.
 *
 * It closes when the president closes it or, without any stored change, when the meeting ends:
 * closedAt stays null and the end of the meeting is the closing time (see closingTime).
 */
@Entity
@Table(name = "meeting_polls")
public class MeetingPoll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Meeting meeting;

    @Column(nullable = false, length = 150)
    private String question;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opened_by_id", nullable = false)
    private User openedBy;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_id")
    private User closedBy;

    public MeetingPoll() {}

    public MeetingPoll(Meeting meeting, String question, String description, User openedBy, LocalDateTime openedAt) {
        this.meeting = meeting;
        this.question = question;
        this.description = description;
        this.openedBy = openedBy;
        this.openedAt = openedAt;
    }

    /** When the vote closed or will close: by the president, or at the end (or cancellation) of the meeting. */
    public LocalDateTime closingTime() {
        if (closedAt != null) return closedAt;
        if (meeting.getCanceledAt() != null && meeting.getCanceledAt().isBefore(meeting.getEndsAt())) return meeting.getCanceledAt();
        return meeting.getEndsAt();
    }

    /** Closed by the president, or because the meeting ended or was canceled; a meeting moved to a later start keeps it open. */
    public boolean isClosedAt(LocalDateTime now) {
        Meeting.MeetingStatus status = meeting.statusAt(now);
        return closedAt != null || status == Meeting.MeetingStatus.ENDED || status == Meeting.MeetingStatus.CANCELED;
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Meeting getMeeting() { return meeting; }
    public void setMeeting(Meeting meeting) { this.meeting = meeting; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public User getOpenedBy() { return openedBy; }
    public void setOpenedBy(User openedBy) { this.openedBy = openedBy; }

    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime openedAt) { this.openedAt = openedAt; }

    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }

    public User getClosedBy() { return closedBy; }
    public void setClosedBy(User closedBy) { this.closedBy = closedBy; }
}
