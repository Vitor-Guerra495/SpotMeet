package com.spotmeet.backend.model;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Meeting scheduled by the president of a committee.
 *
 * The status is derived from the clock and canceledAt, it is not stored.
 * Deleting the committee removes its meetings in the database.
 */
@Entity
@Table(name = "meetings")
public class Meeting {

    public enum MeetingStatus {
        SCHEDULED,
        IN_PROGRESS,
        ENDED,
        CANCELED
    }

    public enum MeetingPriority {
        LOW,
        MEDIUM,
        HIGH
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "committee_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Committee committee;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 2000)
    private String agenda;

    @Column(length = 100)
    private String location;

    @Column(length = 300)
    private String link;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MeetingPriority priority = MeetingPriority.MEDIUM;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "attendance_list_generated_at")
    private LocalDateTime attendanceListGeneratedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_list_generated_by_id")
    private User attendanceListGeneratedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Meeting() {}

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public MeetingStatus statusAt(LocalDateTime now) {
        if (canceledAt != null) return MeetingStatus.CANCELED;
        if (now.isBefore(startsAt)) return MeetingStatus.SCHEDULED;
        if (now.isAfter(endsAt)) return MeetingStatus.ENDED;
        return MeetingStatus.IN_PROGRESS;
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Committee getCommittee() { return committee; }
    public void setCommittee(Committee committee) { this.committee = committee; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAgenda() { return agenda; }
    public void setAgenda(String agenda) { this.agenda = agenda; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public MeetingPriority getPriority() { return priority; }
    public void setPriority(MeetingPriority priority) { this.priority = priority; }

    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }

    public LocalDateTime getEndsAt() { return endsAt; }
    public void setEndsAt(LocalDateTime endsAt) { this.endsAt = endsAt; }

    public LocalDateTime getCanceledAt() { return canceledAt; }
    public void setCanceledAt(LocalDateTime canceledAt) { this.canceledAt = canceledAt; }

    public LocalDateTime getAttendanceListGeneratedAt() { return attendanceListGeneratedAt; }
    public void setAttendanceListGeneratedAt(LocalDateTime value) { this.attendanceListGeneratedAt = value; }

    public User getAttendanceListGeneratedBy() { return attendanceListGeneratedBy; }
    public void setAttendanceListGeneratedBy(User user) { this.attendanceListGeneratedBy = user; }

    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
