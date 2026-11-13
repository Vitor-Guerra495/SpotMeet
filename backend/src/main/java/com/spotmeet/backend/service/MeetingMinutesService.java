package com.spotmeet.backend.service;

import com.spotmeet.backend.dto.MinutesAnswerDTO;
import com.spotmeet.backend.dto.MinutesRequestDTO;
import com.spotmeet.backend.dto.MinutesResponseDTO;
import com.spotmeet.backend.model.*;
import com.spotmeet.backend.model.Meeting.MeetingStatus;
import com.spotmeet.backend.model.MeetingMinutes.MinutesStatus;
import com.spotmeet.backend.repository.MeetingMinutesRepository;
import com.spotmeet.backend.repository.MeetingParticipantRepository;
import com.spotmeet.backend.repository.MinutesResponseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Minutes of a meeting: write, send for approval, answer, close and send again.
 *
 * Rules:
 *   - Only the president writes, and only after the meeting ends; a canceled meeting has no minutes.
 *   - The text has three parts: topics, decisions (both required to send) and optional notes.
 *   - Present people (entry recorded) answer, except the writer. Rejecting requires a reason.
 *   - The president closes the approval: approved when approvals are more than rejections
 *     among who answered; a tie or no answer rejects it.
 *   - A rejected minutes stays REJECTED until the president sends the corrected text, which clears the answers.
 *   - An approved minutes can no longer change.
 */
@Service
public class MeetingMinutesService {

    @Autowired private MeetingMinutesRepository minutesRepo;
    @Autowired private MinutesResponseRepository responseRepo;
    @Autowired private MeetingParticipantRepository participantRepo;
    @Autowired private EmailService emailService;
    @Autowired private MeetingAccess access;

    private void requireEnded(Meeting meeting, LocalDateTime now) {
        MeetingStatus status = meeting.statusAt(now);
        if (status == MeetingStatus.CANCELED) {
            throw new IllegalStateException("Reunião cancelada não tem ata.");
        }
        if (status != MeetingStatus.ENDED) {
            throw new IllegalStateException("A ata só pode ser escrita depois que a reunião terminar.");
        }
    }

    private void applyText(MeetingMinutes minutes, MinutesRequestDTO dto) {
        minutes.setTopics(access.clean(dto.getTopics()));
        minutes.setDecisions(access.clean(dto.getDecisions()));
        minutes.setNotes(access.clean(dto.getNotes()));
    }

    private MinutesResponseDTO view(Meeting meeting, User viewer) {
        List<MeetingParticipant> participants = participantRepo.findByMeetingIdOrderByUserNameAsc(meeting.getId());
        MeetingMinutes minutes = minutesRepo.findByMeetingId(meeting.getId()).orElse(null);
        List<MinutesResponse> responses = minutes == null ? List.of() : responseRepo.findByMinutesIdOrderByRespondedAtAsc(minutes.getId());
        return MinutesResponseDTO.from(meeting.getId(), minutes, participants, responses, viewer.getId(),
                access.isPresident(viewer, meeting.getCommittee()));
    }

    private List<User> presentPeople(Meeting meeting) {
        return participantRepo.findByMeetingIdOrderByUserNameAsc(meeting.getId()).stream()
                .filter(p -> p.getEnteredAt() != null).map(MeetingParticipant::getUser).toList();
    }

    @Transactional(readOnly = true)
    public MinutesResponseDTO get(Long meetingId, String email) {
        Meeting meeting = access.findMeeting(meetingId);
        User user = access.findUser(email);
        access.requireViewer(user, meeting.getCommittee());
        return view(meeting, user);
    }

    /** Saves the draft; only the president sees it until it is sent. */
    @Transactional
    public MinutesResponseDTO saveDraft(Long meetingId, String email, MinutesRequestDTO dto) {
        Meeting meeting = access.findMeeting(meetingId);
        User president = access.findUser(email);
        access.requirePresident(president, meeting.getCommittee());
        LocalDateTime now = LocalDateTime.now();
        requireEnded(meeting, now);

        MeetingMinutes minutes = minutesRepo.findForUpdate(meetingId).orElseGet(() -> new MeetingMinutes(meeting, president));
        if (minutes.getStatus() != MinutesStatus.DRAFT) {
            throw new IllegalStateException(minutes.getStatus() == MinutesStatus.APPROVED
                ? "Uma ata aprovada não pode mais ser editada."
                : "A ata já foi enviada. As correções são salvas ao reenviar para aprovação.");
        }
        applyText(minutes, dto);
        if (minutes.getTopics() == null && minutes.getDecisions() == null && minutes.getNotes() == null) {
            throw new IllegalStateException("Escreva algo na ata antes de salvar o rascunho.");
        }
        minutes.setWrittenBy(president);
        minutes.setUpdatedAt(now);
        minutesRepo.save(minutes);

        access.audit(president, "MEETING_MINUTES_DRAFT_SAVED", meeting, "");
        return view(meeting, president);
    }

    /** Sends the draft for approval, or sends the corrected text of a rejected minutes. */
    @Transactional
    public MinutesResponseDTO submit(Long meetingId, String email, MinutesRequestDTO dto) {
        Meeting meeting = access.findMeeting(meetingId);
        User president = access.findUser(email);
        access.requirePresident(president, meeting.getCommittee());
        LocalDateTime now = LocalDateTime.now();
        requireEnded(meeting, now);

        MeetingMinutes minutes = minutesRepo.findForUpdate(meetingId).orElseGet(() -> new MeetingMinutes(meeting, president));
        if (minutes.getStatus() == MinutesStatus.IN_APPROVAL) {
            throw new IllegalStateException("A ata já está em aprovação.");
        }
        if (minutes.getStatus() == MinutesStatus.APPROVED) {
            throw new IllegalStateException("Uma ata aprovada não pode mais ser editada.");
        }
        applyText(minutes, dto);
        if (minutes.getTopics() == null) throw new IllegalStateException("Registre os assuntos tratados.");
        if (minutes.getDecisions() == null) throw new IllegalStateException("Registre as decisões tomadas.");

        List<User> readers = presentPeople(meeting).stream().filter(u -> !u.getId().equals(president.getId())).toList();
        if (readers.isEmpty()) {
            throw new IllegalStateException("Não dá para enviar: ninguém além de você registrou entrada nesta reunião.");
        }
        boolean resend = minutes.getStatus() == MinutesStatus.REJECTED;
        if (resend) responseRepo.deleteByMinutesId(minutes.getId());

        minutes.setWrittenBy(president);
        minutes.setStatus(MinutesStatus.IN_APPROVAL);
        minutes.setUpdatedAt(now);
        minutes.setSubmittedAt(now);
        minutes.setClosedAt(null);
        minutesRepo.save(minutes);

        MeetingText.Mail mail = MeetingText.mail(meeting, MeetingService.appLink(meeting.getId()));
        for (User reader : readers) {
            emailService.sendMinutesForApprovalEmail(reader.getEmail(), reader.getName(), mail, president.getName(), now);
        }
        access.audit(president, resend ? "MEETING_MINUTES_RESUBMITTED" : "MEETING_MINUTES_SUBMITTED", meeting,
                " | Pessoas avisadas: " + readers.size());
        return view(meeting, president);
    }

    /** Approve or reject, once per round, by a present person who did not write the minutes. */
    @Transactional
    public MinutesResponseDTO answer(Long meetingId, String email, MinutesAnswerDTO dto) {
        Meeting meeting = access.findMeeting(meetingId);
        User user = access.findUser(email);
        access.requireViewer(user, meeting.getCommittee());

        MeetingMinutes minutes = minutesRepo.findForUpdate(meetingId)
                .filter(m -> m.getStatus() == MinutesStatus.IN_APPROVAL)
                .orElseThrow(() -> new IllegalStateException("A ata não está em aprovação."));
        if (minutes.getWrittenBy().getId().equals(user.getId())) {
            throw new IllegalStateException("Você não responde à sua própria ata.");
        }
        boolean present = participantRepo.findByMeetingIdAndUserId(meetingId, user.getId())
                .filter(p -> p.getEnteredAt() != null).isPresent();
        if (!present) {
            throw new IllegalStateException("Só quem registrou entrada na reunião pode responder à ata.");
        }
        if (responseRepo.existsByMinutesIdAndUserId(minutes.getId(), user.getId())) {
            throw new IllegalStateException("Você já respondeu a esta ata.");
        }
        boolean approved = dto.getApproved();
        String reason = access.clean(dto.getReason());
        if (!approved && reason == null) {
            throw new IllegalStateException("Informe o motivo da reprovação.");
        }
        responseRepo.save(new MinutesResponse(minutes, user, approved, approved ? null : reason, LocalDateTime.now()));

        access.audit(user, approved ? "MEETING_MINUTES_APPROVED_BY_MEMBER" : "MEETING_MINUTES_REJECTED_BY_MEMBER", meeting, "");
        return view(meeting, user);
    }

    /** Closes the approval with the answers received so far. */
    @Transactional
    public MinutesResponseDTO close(Long meetingId, String email) {
        Meeting meeting = access.findMeeting(meetingId);
        User president = access.findUser(email);
        access.requirePresident(president, meeting.getCommittee());

        MeetingMinutes minutes = minutesRepo.findForUpdate(meetingId)
                .filter(m -> m.getStatus() == MinutesStatus.IN_APPROVAL)
                .orElseThrow(() -> new IllegalStateException("A ata não está em aprovação."));
        List<MinutesResponse> responses = responseRepo.findByMinutesIdOrderByRespondedAtAsc(minutes.getId());
        long approvals = responses.stream().filter(MinutesResponse::isApproved).count();
        long rejections = responses.size() - approvals;
        boolean approved = approvals > rejections;

        LocalDateTime now = LocalDateTime.now();
        minutes.setStatus(approved ? MinutesStatus.APPROVED : MinutesStatus.REJECTED);
        minutes.setClosedAt(now);
        minutesRepo.save(minutes);

        List<User> present = presentPeople(meeting);
        Long writerId = minutes.getWrittenBy().getId();
        long pending = present.stream().filter(u -> !u.getId().equals(writerId)).count() - responses.size();
        String result = resultText(approvals, rejections, Math.max(0, pending));
        MeetingText.Mail mail = MeetingText.mail(meeting, MeetingService.appLink(meeting.getId()));
        if (approved) {
            for (User person : present) {
                emailService.sendMinutesApprovedEmail(person.getEmail(), person.getName(), mail, result, now);
            }
        } else {
            Map<Long, String> names = present.stream().collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));
            List<String[]> reasons = new ArrayList<>();
            for (MinutesResponse r : responses) {
                if (!r.isApproved()) reasons.add(new String[] { names.getOrDefault(r.getUser().getId(), r.getUser().getName()), r.getReason() });
            }
            String why = responses.isEmpty() ? "Ninguém respondeu." : approvals == rejections
                ? "Houve empate entre as respostas." : "A maioria de quem respondeu reprovou.";
            for (User person : present) {
                emailService.sendMinutesRejectedEmail(person.getEmail(), person.getName(), mail, result, why, reasons, now,
                        access.isPresident(person, meeting.getCommittee()));
            }
        }
        access.audit(president, approved ? "MEETING_MINUTES_APPROVED" : "MEETING_MINUTES_REJECTED", meeting,
                " | Aprovaram: " + approvals + " | Reprovaram: " + rejections);
        return view(meeting, president);
    }

    /** "4 aprovaram, 1 reprovou, 2 sem resposta." */
    static String resultText(long approvals, long rejections, long pending) {
        return approvals + (approvals == 1 ? " aprovou, " : " aprovaram, ")
                + rejections + (rejections == 1 ? " reprovou, " : " reprovaram, ")
                + pending + " sem resposta.";
    }
}
