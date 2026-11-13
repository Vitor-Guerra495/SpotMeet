package com.spotmeet.backend.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Repetition rule of a meeting and the data copied into each new date.
 *
 * The server creates the meetings of the next three months and adds the following ones over time.
 * Editing or canceling a meeting of the series changes only that meeting, never the rule.
 */
@Entity
@Table(name = "meeting_series")
public class MeetingSeries {

    public enum Frequency {
        DAILY,
        WEEKLY,
        MONTHLY,
        YEARLY
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "committee_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Committee committee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Frequency frequency;

    @Column(name = "repeat_interval", nullable = false)
    private int repeatInterval = 1;

    // ISO days of the week (1 = Monday ... 7 = Sunday) separated by commas; weekly rules only.
    @Column(name = "week_days", length = 20)
    private String weekDays;

    // Monthly rules: true repeats on the same weekday of the month (first Monday), false on the same day (day 2).
    @ColumnDefault("false")
    @Column(name = "monthly_by_weekday", nullable = false)
    private boolean monthlyByWeekday;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "max_occurrences")
    private Integer maxOccurrences;

    @Column(name = "first_starts_at", nullable = false)
    private LocalDateTime firstStartsAt;

    @Column(name = "duration_minutes", nullable = false)
    private long durationMinutes;

    @Column(name = "last_starts_at")
    private LocalDateTime lastStartsAt;

    @Column(name = "created_count", nullable = false)
    private int createdCount;

    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean finished;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 2000)
    private String agenda;

    @Column(length = 100)
    private String location;

    @Column(length = 300)
    private String link;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Meeting.MeetingModality modality;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Meeting.MeetingPriority priority = Meeting.MeetingPriority.MEDIUM;

    @ColumnDefault("false")
    @Column(name = "all_day", nullable = false)
    private boolean allDay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private MeetingFile attachment;

    // Ids of the chosen guests separated by commas; each new date invites those still active in the committee.
    @Column(name = "invited_user_ids", length = 4000)
    private String invitedUserIds;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public MeetingSeries() {}

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Committee getCommittee() { return committee; }
    public void setCommittee(Committee committee) { this.committee = committee; }

    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }

    public Frequency getFrequency() { return frequency; }
    public void setFrequency(Frequency frequency) { this.frequency = frequency; }

    public int getRepeatInterval() { return repeatInterval; }
    public void setRepeatInterval(int repeatInterval) { this.repeatInterval = repeatInterval; }

    public String getWeekDays() { return weekDays; }
    public void setWeekDays(String weekDays) { this.weekDays = weekDays; }

    public boolean isMonthlyByWeekday() { return monthlyByWeekday; }
    public void setMonthlyByWeekday(boolean monthlyByWeekday) { this.monthlyByWeekday = monthlyByWeekday; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public Integer getMaxOccurrences() { return maxOccurrences; }
    public void setMaxOccurrences(Integer maxOccurrences) { this.maxOccurrences = maxOccurrences; }

    public LocalDateTime getFirstStartsAt() { return firstStartsAt; }
    public void setFirstStartsAt(LocalDateTime firstStartsAt) { this.firstStartsAt = firstStartsAt; }

    public long getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(long durationMinutes) { this.durationMinutes = durationMinutes; }

    public LocalDateTime getLastStartsAt() { return lastStartsAt; }
    public void setLastStartsAt(LocalDateTime lastStartsAt) { this.lastStartsAt = lastStartsAt; }

    public int getCreatedCount() { return createdCount; }
    public void setCreatedCount(int createdCount) { this.createdCount = createdCount; }

    public boolean isFinished() { return finished; }
    public void setFinished(boolean finished) { this.finished = finished; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAgenda() { return agenda; }
    public void setAgenda(String agenda) { this.agenda = agenda; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public Meeting.MeetingModality getModality() { return modality; }
    public void setModality(Meeting.MeetingModality modality) { this.modality = modality; }

    public Meeting.MeetingPriority getPriority() { return priority; }
    public void setPriority(Meeting.MeetingPriority priority) { this.priority = priority; }

    public boolean isAllDay() { return allDay; }
    public void setAllDay(boolean allDay) { this.allDay = allDay; }

    public MeetingFile getAttachment() { return attachment; }
    public void setAttachment(MeetingFile attachment) { this.attachment = attachment; }

    public Set<Long> getInvitedUserIds() {
        Set<Long> ids = new LinkedHashSet<>();
        if (invitedUserIds == null || invitedUserIds.isBlank()) return ids;
        for (String id : invitedUserIds.split(",")) ids.add(Long.valueOf(id.trim()));
        return ids;
    }
    public void setInvitedUserIds(Set<Long> ids) {
        this.invitedUserIds = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
