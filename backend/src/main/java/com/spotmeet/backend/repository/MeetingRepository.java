package com.spotmeet.backend.repository;

import com.spotmeet.backend.model.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    List<Meeting> findByCommitteeIdOrderByStartsAtDesc(Long committeeId);
}
