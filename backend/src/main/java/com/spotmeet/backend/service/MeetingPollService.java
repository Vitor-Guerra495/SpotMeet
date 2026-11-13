package com.spotmeet.backend.service;

import com.spotmeet.backend.dto.PollRequestDTO;
import com.spotmeet.backend.dto.PollResponseDTO;
import com.spotmeet.backend.dto.PollVoteDTO;
import com.spotmeet.backend.model.*;
import com.spotmeet.backend.model.Meeting.MeetingStatus;
import com.spotmeet.backend.model.PollVote.VoteChoice;
import com.spotmeet.backend.repository.MeetingParticipantRepository;
import com.spotmeet.backend.repository.MeetingPollRepository;
import com.spotmeet.backend.repository.PollVoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Votes during a meeting: open, vote, close and read the result.
 *
 * Rules:
 *   - Only the president opens a vote, and only while the meeting is in progress.
 *   - Present people (entry recorded) vote once: Sim, Não or Abstenção. The vote is nominal and cannot change;
 *     an abstention can have a justification of up to 280 characters.
 *   - The vote closes when the president closes it or when the meeting ends; no scheduled task is needed.
 *   - Sim above Não approves, Não above Sim rejects, equal counts are a tie and no vote at all is "Sem votos".
 */
@Service
public class MeetingPollService {

    @Autowired private MeetingPollRepository pollRepo;
    @Autowired private PollVoteRepository voteRepo;
    @Autowired private MeetingParticipantRepository participantRepo;
    @Autowired private MeetingAccess access;

    private List<PollResponseDTO> view(Meeting meeting, User viewer, LocalDateTime now) {
        List<MeetingPoll> polls = pollRepo.findByMeetingIdOrderByOpenedAtAsc(meeting.getId());
        if (polls.isEmpty()) return List.of();
        List<MeetingParticipant> participants = participantRepo.findByMeetingIdOrderByUserNameAsc(meeting.getId());
        Map<Long, List<PollVote>> votes = voteRepo.findByPollIdInOrderByVotedAtAsc(polls.stream().map(MeetingPoll::getId).toList())
                .stream().collect(Collectors.groupingBy(v -> v.getPoll().getId()));
        boolean president = access.isPresident(viewer, meeting.getCommittee());
        return polls.stream()
                .map(p -> PollResponseDTO.from(p, participants, votes.getOrDefault(p.getId(), List.of()), viewer.getId(), president, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PollResponseDTO> list(Long meetingId, String email) {
        Meeting meeting = access.findMeeting(meetingId);
        User user = access.findUser(email);
        access.requireViewer(user, meeting.getCommittee());
        return view(meeting, user, LocalDateTime.now());
    }

    @Transactional
    public List<PollResponseDTO> open(Long meetingId, String email, PollRequestDTO dto) {
        Meeting meeting = access.findMeeting(meetingId);
        User president = access.findUser(email);
        access.requirePresident(president, meeting.getCommittee());
        LocalDateTime now = LocalDateTime.now();
        MeetingStatus status = meeting.statusAt(now);
        if (status != MeetingStatus.IN_PROGRESS) {
            throw new IllegalStateException(status == MeetingStatus.ENDED
                ? "A reunião terminou. Só é possível abrir uma votação com a reunião em andamento."
                : "Só é possível abrir uma votação com a reunião em andamento.");
        }
        String question = access.clean(dto.getQuestion());
        if (question == null) throw new IllegalStateException("Escreva a pergunta da votação.");
        MeetingPoll poll = pollRepo.save(new MeetingPoll(meeting, question, access.clean(dto.getDescription()), president, now));

        access.audit(president, "MEETING_POLL_OPENED", meeting, " | Votação ID: " + poll.getId());
        return view(meeting, president, now);
    }

    @Transactional
    public List<PollResponseDTO> vote(Long meetingId, Long pollId, String email, PollVoteDTO dto) {
        Meeting meeting = access.findMeeting(meetingId);
        User user = access.findUser(email);
        access.requireActiveMember(user, meeting.getCommittee());
        LocalDateTime now = LocalDateTime.now();

        MeetingPoll poll = pollRepo.findForUpdate(meetingId, pollId)
                .orElseThrow(() -> new IllegalArgumentException("Votação não encontrada."));
        if (poll.isClosedAt(now)) {
            throw new IllegalStateException("Esta votação já foi encerrada.");
        }
        if (meeting.statusAt(now) != MeetingStatus.IN_PROGRESS) {
            throw new IllegalStateException("Só é possível votar com a reunião em andamento.");
        }
        boolean present = participantRepo.findByMeetingIdAndUserId(meetingId, user.getId())
                .filter(p -> p.getEnteredAt() != null).isPresent();
        if (!present) {
            throw new IllegalStateException("Só quem registrou entrada na reunião pode votar.");
        }
        if (voteRepo.existsByPollIdAndUserId(pollId, user.getId())) {
            throw new IllegalStateException("Você já votou nesta votação. O voto não pode ser trocado.");
        }
        String justification = dto.getChoice() == VoteChoice.ABSTAIN ? access.clean(dto.getJustification()) : null;
        voteRepo.save(new PollVote(poll, user, dto.getChoice(), justification, now));

        access.audit(user, "MEETING_POLL_VOTED", meeting, " | Votação ID: " + pollId);
        return view(meeting, user, now);
    }

    @Transactional
    public List<PollResponseDTO> close(Long meetingId, Long pollId, String email) {
        Meeting meeting = access.findMeeting(meetingId);
        User president = access.findUser(email);
        access.requirePresident(president, meeting.getCommittee());
        LocalDateTime now = LocalDateTime.now();

        MeetingPoll poll = pollRepo.findForUpdate(meetingId, pollId)
                .orElseThrow(() -> new IllegalArgumentException("Votação não encontrada."));
        if (poll.isClosedAt(now)) {
            throw new IllegalStateException("Esta votação já foi encerrada.");
        }
        poll.setClosedAt(now);
        poll.setClosedBy(president);
        pollRepo.save(poll);

        access.audit(president, "MEETING_POLL_CLOSED", meeting, " | Votação ID: " + pollId);
        return view(meeting, president, now);
    }
}
