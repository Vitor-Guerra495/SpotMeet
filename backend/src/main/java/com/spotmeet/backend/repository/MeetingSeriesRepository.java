package com.spotmeet.backend.repository;

import com.spotmeet.backend.model.MeetingSeries;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeetingSeriesRepository extends JpaRepository<MeetingSeries, Long> {

    List<MeetingSeries> findByFinishedFalse();

    boolean existsByAttachmentId(Long fileId);
}
