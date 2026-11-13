package com.spotmeet.backend.model;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Nominal vote of a present person. One vote per person and poll, and it cannot be changed;
 * the justification is optional and only exists for an abstention.
 */
@Entity
@Table(
    name = "poll_votes",
    uniqueConstraints = @UniqueConstraint(name = "uq_poll_vote", columnNames = {"poll_id", "user_id"})
)
public class PollVote {

    public enum VoteChoice {
        YES,
        NO,
        ABSTAIN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MeetingPoll poll;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private VoteChoice choice;

    @Column(length = 280)
    private String justification;

    @Column(name = "voted_at", nullable = false)
    private LocalDateTime votedAt;

    public PollVote() {}

    public PollVote(MeetingPoll poll, User user, VoteChoice choice, String justification, LocalDateTime votedAt) {
        this.poll = poll;
        this.user = user;
        this.choice = choice;
        this.justification = justification;
        this.votedAt = votedAt;
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public MeetingPoll getPoll() { return poll; }
    public void setPoll(MeetingPoll poll) { this.poll = poll; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public VoteChoice getChoice() { return choice; }
    public void setChoice(VoteChoice choice) { this.choice = choice; }

    public String getJustification() { return justification; }
    public void setJustification(String justification) { this.justification = justification; }

    public LocalDateTime getVotedAt() { return votedAt; }
    public void setVotedAt(LocalDateTime votedAt) { this.votedAt = votedAt; }
}
