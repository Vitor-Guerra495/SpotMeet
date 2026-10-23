package com.spotmeet.backend.service;

import com.spotmeet.backend.dto.MeetingResponseDTO;
import com.spotmeet.backend.model.*;
import com.spotmeet.backend.model.Meeting.MeetingStatus;
import com.spotmeet.backend.model.MeetingParticipant.InvitationResponse;
import com.spotmeet.backend.repository.MeetingParticipantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Answer to a meeting invitation.
 *
 * Rules:
 *   - Only an invited, active member of the committee answers.
 *   - The answer can be changed until the meeting ends or is canceled.
 *   - A comment is kept only when the invitation is declined.
 */
@Service
public class MeetingInvitationService {

    @Autowired private MeetingParticipantRepository participantRepo;
    @Autowired private MeetingAccess access;

    @Transactional
    public MeetingResponseDTO respond(Long meetingId, String email, boolean accept, String comment) {
        Meeting meeting = access.findMeeting(meetingId);
        User user = access.findUser(email);
        access.requireActiveMember(user, meeting.getCommittee());

        LocalDateTime now = LocalDateTime.now();
        MeetingStatus status = meeting.statusAt(now);
        if (status == MeetingStatus.CANCELED || status == MeetingStatus.ENDED) {
            throw new IllegalStateException("Esta reunião não aceita mais respostas.");
        }
        MeetingParticipant participant = participantRepo.findForUpdate(meetingId, user.getId())
                .orElseThrow(() -> new IllegalStateException("Você não foi convidado para esta reunião."));
        participant.setResponse(accept ? InvitationResponse.ACCEPTED : InvitationResponse.DECLINED);
        participant.setResponseComment(accept ? null : access.clean(comment));
        participant.setRespondedAt(now);
        participantRepo.save(participant);

        access.audit(user, "MEETING_INVITATION_ANSWERED", meeting, " | Resposta: " + participant.getResponse());
        return access.detail(meeting, user, now);
    }
}
