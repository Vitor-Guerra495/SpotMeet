package com.spotmeet.backend.model;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Minutes of a meeting, written by the committee president after the meeting ends.
 *
 * One minutes per meeting. The present and absent people are not stored: they come from the entry records.
 * A rejected minutes stays REJECTED while the president corrects it; sending it again starts a new round.
 */
@Entity
@Table(name = "meeting_minutes")
public class MeetingMinutes {

    public enum MinutesStatus {
        DRAFT,
        IN_APPROVAL,
        APPROVED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Meeting meeting;

    @Column(length = 4000)
    private String topics;

    @Column(length = 4000)
    private String decisions;

    @Column(length = 2000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MinutesStatus status = MinutesStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "written_by_id", nullable = false)
    private User writtenBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    public MeetingMinutes() {}

    public MeetingMinutes(Meeting meeting, User writtenBy) {
        this.meeting = meeting;
        this.writtenBy = writtenBy;
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Meeting getMeeting() { return meeting; }
    public void setMeeting(Meeting meeting) { this.meeting = meeting; }

    public String getTopics() { return topics; }
    public void setTopics(String topics) { this.topics = topics; }

    public String getDecisions() { return decisions; }
    public void setDecisions(String decisions) { this.decisions = decisions; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public MinutesStatus getStatus() { return status; }
    public void setStatus(MinutesStatus status) { this.status = status; }

    public User getWrittenBy() { return writtenBy; }
    public void setWrittenBy(User writtenBy) { this.writtenBy = writtenBy; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
}
