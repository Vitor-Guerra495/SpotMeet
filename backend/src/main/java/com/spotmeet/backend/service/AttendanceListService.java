package com.spotmeet.backend.service;

import com.spotmeet.backend.dto.AttendanceListDTO;
import com.spotmeet.backend.model.*;
import com.spotmeet.backend.model.Meeting.MeetingStatus;
import com.spotmeet.backend.repository.MeetingParticipantRepository;
import com.spotmeet.backend.repository.MeetingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Attendance list of a meeting.
 *
 * Rules:
 *   - Only the president generates it, after the meeting starts and never for a canceled meeting.
 *   - Active members and organization leaders read it once it was generated.
 *   - A person is present when the entry was recorded.
 */
@Service
public class AttendanceListService {

    @Autowired private MeetingRepository meetingRepo;
    @Autowired private MeetingParticipantRepository participantRepo;
    @Autowired private MeetingAccess access;

    @Transactional
    public AttendanceListDTO generate(Long meetingId, String email) {
        Meeting meeting = access.findMeeting(meetingId);
        User president = access.findUser(email);
        access.requirePresident(president, meeting.getCommittee());

        LocalDateTime now = LocalDateTime.now();
        MeetingStatus status = meeting.statusAt(now);
        if (status == MeetingStatus.CANCELED) {
            throw new IllegalStateException("Reunião cancelada não tem lista de presença.");
        }
        if (status == MeetingStatus.SCHEDULED) {
            throw new IllegalStateException("A lista só pode ser gerada depois que a reunião começar.");
        }
        meeting.setAttendanceListGeneratedAt(now);
        meeting.setAttendanceListGeneratedBy(president);
        meetingRepo.save(meeting);

        access.audit(president, "MEETING_ATTENDANCE_LIST_GENERATED", meeting, "");
        return AttendanceListDTO.from(meeting, participantRepo.findByMeetingIdOrderByUserNameAsc(meetingId), now);
    }

    @Transactional(readOnly = true)
    public AttendanceListDTO get(Long meetingId, String email) {
        Meeting meeting = access.findMeeting(meetingId);
        User user = access.findUser(email);
        access.requireViewer(user, meeting.getCommittee());
        if (meeting.getAttendanceListGeneratedAt() == null) {
            throw new IllegalStateException("A lista de presença ainda não foi gerada.");
        }
        return AttendanceListDTO.from(meeting, participantRepo.findByMeetingIdOrderByUserNameAsc(meetingId), LocalDateTime.now());
    }
}
