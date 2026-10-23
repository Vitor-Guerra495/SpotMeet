package com.spotmeet.backend.model;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Invitation of a committee member to a meeting, with the answer and the attendance records.
 *
 * One row per user and meeting. A person is present when enteredAt is filled.
 */
@Entity
@Table(
    name = "meeting_participants",
    uniqueConstraints = @UniqueConstraint(name = "uq_meeting_participant", columnNames = {"meeting_id", "user_id"})
)
public class MeetingParticipant {

    public enum InvitationResponse {
        PENDING,
        ACCEPTED,
        DECLINED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationResponse response = InvitationResponse.PENDING;

    @Column(name = "response_comment", length = 200)
    private String responseComment;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "entered_at")
    private LocalDateTime enteredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entered_by_id")
    private User enteredBy;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "left_by_id")
    private User leftBy;

    public MeetingParticipant() {}

    public MeetingParticipant(Meeting meeting, User user) {
        this.meeting = meeting;
        this.user = user;
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Meeting getMeeting() { return meeting; }
    public void setMeeting(Meeting meeting) { this.meeting = meeting; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public InvitationResponse getResponse() { return response; }
    public void setResponse(InvitationResponse response) { this.response = response; }

    public String getResponseComment() { return responseComment; }
    public void setResponseComment(String responseComment) { this.responseComment = responseComment; }

    public LocalDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = respondedAt; }

    public LocalDateTime getEnteredAt() { return enteredAt; }
    public void setEnteredAt(LocalDateTime enteredAt) { this.enteredAt = enteredAt; }

    public User getEnteredBy() { return enteredBy; }
    public void setEnteredBy(User enteredBy) { this.enteredBy = enteredBy; }

    public LocalDateTime getLeftAt() { return leftAt; }
    public void setLeftAt(LocalDateTime leftAt) { this.leftAt = leftAt; }

    public User getLeftBy() { return leftBy; }
    public void setLeftBy(User leftBy) { this.leftBy = leftBy; }
}
