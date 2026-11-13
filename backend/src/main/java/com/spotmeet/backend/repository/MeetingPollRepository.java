package com.spotmeet.backend.repository;

import com.spotmeet.backend.model.MeetingPoll;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MeetingPollRepository extends JpaRepository<MeetingPoll, Long> {

    List<MeetingPoll> findByMeetingIdOrderByOpenedAtAsc(Long meetingId);

    /** Serializes votes and the closing of the same poll. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM MeetingPoll p WHERE p.id = :pollId AND p.meeting.id = :meetingId")
    Optional<MeetingPoll> findForUpdate(@Param("meetingId") Long meetingId, @Param("pollId") Long pollId);
}
