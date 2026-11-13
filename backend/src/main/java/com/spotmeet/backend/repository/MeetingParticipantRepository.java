package com.spotmeet.backend.repository;

import com.spotmeet.backend.model.CommitteeMember.CommitteeMembershipStatus;
import com.spotmeet.backend.model.MeetingParticipant;
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
public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, Long> {

    List<MeetingParticipant> findByMeetingIdOrderByUserNameAsc(Long meetingId);

    List<MeetingParticipant> findByMeetingIdIn(Collection<Long> meetingIds);

    Optional<MeetingParticipant> findByMeetingIdAndUserId(Long meetingId, Long userId);

    /** Serializes entry, exit and answer writes of the same person in the same meeting. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM MeetingParticipant p WHERE p.meeting.id = :meetingId AND p.user.id = :userId")
    Optional<MeetingParticipant> findForUpdate(@Param("meetingId") Long meetingId, @Param("userId") Long userId);

    /** Invitations of the user in committees where the membership is still active. */
    @Query("SELECT p FROM MeetingParticipant p JOIN FETCH p.meeting m JOIN FETCH m.committee c "
         + "WHERE p.user.id = :userId AND EXISTS (SELECT cm.id FROM CommitteeMember cm "
         + "WHERE cm.user.id = :userId AND cm.committee.id = c.id AND cm.status = :status) "
         + "ORDER BY m.startsAt DESC")
    List<MeetingParticipant> findActiveByUser(@Param("userId") Long userId,
                                              @Param("status") CommitteeMembershipStatus status);

    /** Meetings where the user registered an entry, newest first (individual report). */
    @Query("SELECT p FROM MeetingParticipant p JOIN FETCH p.meeting m JOIN FETCH m.committee c "
         + "WHERE p.user.id = :userId AND p.enteredAt IS NOT NULL ORDER BY m.startsAt DESC")
    List<MeetingParticipant> findEnteredByUser(@Param("userId") Long userId);

    /** Unanswered invitations to meetings that were not canceled and have not ended. */
    @Query("SELECT COUNT(p) FROM MeetingParticipant p JOIN p.meeting m "
         + "WHERE p.user.id = :userId AND p.response = :pending AND m.canceledAt IS NULL AND m.endsAt > :now "
         + "AND EXISTS (SELECT cm.id FROM CommitteeMember cm "
         + "WHERE cm.user.id = :userId AND cm.committee.id = m.committee.id AND cm.status = :status)")
    long countPending(@Param("userId") Long userId, @Param("pending") MeetingParticipant.InvitationResponse pending,
                      @Param("now") java.time.LocalDateTime now, @Param("status") CommitteeMembershipStatus status);
}
