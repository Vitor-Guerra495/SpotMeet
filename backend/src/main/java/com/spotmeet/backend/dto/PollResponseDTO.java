package com.spotmeet.backend.dto;

import com.spotmeet.backend.model.MeetingParticipant;
import com.spotmeet.backend.model.MeetingPoll;
import com.spotmeet.backend.model.PollVote;
import com.spotmeet.backend.model.PollVote.VoteChoice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A vote of the meeting as the app shows it.
 *
 * While it is open, only the president sees the live count and who voted; the others see how many
 * can vote and their own vote. After it closes, the nominal result is shown to everyone.
 */
public class PollResponseDTO {

    public enum PollResult {
        APPROVED,
        REJECTED,
        TIE,
        NO_VOTES
    }

    public record Vote(Long userId, String userName, VoteChoice choice, String justification, LocalDateTime votedAt) {}

    public record Person(Long userId, String userName) {}

    private Long id;
    private String question;
    private String description;
    private String openedByName;
    private LocalDateTime openedAt;
    private boolean closed;
    private LocalDateTime closedAt;
    private String closedByName;
    private boolean closedAutomatically;
    private int eligible;
    private int totalVotes;
    private boolean canVote;
    private Vote myVote;
    private PollResult result;
    private Integer yes;
    private Integer no;
    private Integer abstain;
    private List<Vote> votes;
    private List<Person> notVoted;

    public PollResponseDTO() {}

    /**
     * @param participants invitations of the meeting, ordered by name; present people are the ones with an entry
     * @param pollVotes    votes of this poll
     */
    public static PollResponseDTO from(MeetingPoll poll, List<MeetingParticipant> participants, List<PollVote> pollVotes,
                                       Long viewerId, boolean president, LocalDateTime now) {
        PollResponseDTO dto = new PollResponseDTO();
        dto.id = poll.getId();
        dto.question = poll.getQuestion();
        dto.description = poll.getDescription();
        dto.openedByName = poll.getOpenedBy().getName();
        dto.openedAt = poll.getOpenedAt();
        dto.closed = poll.isClosedAt(now);
        if (dto.closed) {
            dto.closedAt = poll.closingTime();
            dto.closedByName = poll.getClosedBy() != null ? poll.getClosedBy().getName() : null;
            dto.closedAutomatically = poll.getClosedAt() == null;
        }

        List<MeetingParticipant> present = participants.stream().filter(p -> p.getEnteredAt() != null).toList();
        dto.eligible = present.size();
        dto.totalVotes = pollVotes.size();
        List<Vote> all = pollVotes.stream()
                .map(v -> new Vote(v.getUser().getId(), v.getUser().getName(), v.getChoice(), v.getJustification(), v.getVotedAt()))
                .toList();
        dto.myVote = all.stream().filter(v -> v.userId().equals(viewerId)).findFirst().orElse(null);
        dto.canVote = !dto.closed && dto.myVote == null && present.stream().anyMatch(p -> p.getUser().getId().equals(viewerId));

        if (dto.closed || president) {
            dto.yes = (int) all.stream().filter(v -> v.choice() == VoteChoice.YES).count();
            dto.no = (int) all.stream().filter(v -> v.choice() == VoteChoice.NO).count();
            dto.abstain = (int) all.stream().filter(v -> v.choice() == VoteChoice.ABSTAIN).count();
            dto.votes = all;
            Set<Long> voters = all.stream().map(Vote::userId).collect(Collectors.toSet());
            dto.notVoted = present.stream().filter(p -> !voters.contains(p.getUser().getId()))
                    .map(p -> new Person(p.getUser().getId(), p.getUser().getName())).toList();
        }
        if (dto.closed) {
            if (all.isEmpty()) dto.result = PollResult.NO_VOTES;
            else if (dto.yes > dto.no) dto.result = PollResult.APPROVED;
            else if (dto.no > dto.yes) dto.result = PollResult.REJECTED;
            else dto.result = PollResult.TIE;
        }
        return dto;
    }

    public Long getId() { return id; }
    public String getQuestion() { return question; }
    public String getDescription() { return description; }
    public String getOpenedByName() { return openedByName; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public boolean isClosed() { return closed; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public String getClosedByName() { return closedByName; }
    public boolean isClosedAutomatically() { return closedAutomatically; }
    public int getEligible() { return eligible; }
    public int getTotalVotes() { return totalVotes; }
    public boolean isCanVote() { return canVote; }
    public Vote getMyVote() { return myVote; }
    public PollResult getResult() { return result; }
    public Integer getYes() { return yes; }
    public Integer getNo() { return no; }
    public Integer getAbstain() { return abstain; }
    public List<Vote> getVotes() { return votes; }
    public List<Person> getNotVoted() { return notVoted; }
}
