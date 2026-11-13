package com.spotmeet.backend.repository;

import com.spotmeet.backend.model.MeetingMinutes;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MeetingMinutesRepository extends JpaRepository<MeetingMinutes, Long> {

    Optional<MeetingMinutes> findByMeetingId(Long meetingId);

    List<MeetingMinutes> findByMeetingIdIn(Collection<Long> meetingIds);

    /** Serializes the changes of the same minutes (answers, closing and sending again). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MeetingMinutes m WHERE m.meeting.id = :meetingId")
    Optional<MeetingMinutes> findForUpdate(@Param("meetingId") Long meetingId);
}
