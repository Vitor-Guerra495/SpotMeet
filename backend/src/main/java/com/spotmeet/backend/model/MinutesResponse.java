package com.spotmeet.backend.model;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Answer of a present person to the minutes in approval. One answer per person in each round;
 * the reason is required when the person rejects.
 */
@Entity
@Table(
    name = "minutes_responses",
    uniqueConstraints = @UniqueConstraint(name = "uq_minutes_response", columnNames = {"minutes_id", "user_id"})
)
public class MinutesResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "minutes_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MeetingMinutes minutes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private boolean approved;

    @Column(length = 300)
    private String reason;

    @Column(name = "responded_at", nullable = false)
    private LocalDateTime respondedAt;

    public MinutesResponse() {}

    public MinutesResponse(MeetingMinutes minutes, User user, boolean approved, String reason, LocalDateTime respondedAt) {
        this.minutes = minutes;
        this.user = user;
        this.approved = approved;
        this.reason = reason;
        this.respondedAt = respondedAt;
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public MeetingMinutes getMinutes() { return minutes; }
    public void setMinutes(MeetingMinutes minutes) { this.minutes = minutes; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public LocalDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = respondedAt; }
}
