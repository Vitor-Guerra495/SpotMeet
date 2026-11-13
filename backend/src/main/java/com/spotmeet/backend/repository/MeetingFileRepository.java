package com.spotmeet.backend.repository;

import com.spotmeet.backend.model.MeetingFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface MeetingFileRepository extends JpaRepository<MeetingFile, Long> {

    /** Files sent for a form that was never saved. */
    @Modifying
    @Query("DELETE FROM MeetingFile f WHERE f.createdAt < :before "
         + "AND NOT EXISTS (SELECT m.id FROM Meeting m WHERE m.attachment = f) "
         + "AND NOT EXISTS (SELECT s.id FROM MeetingSeries s WHERE s.attachment = f)")
    int deleteUnusedBefore(@Param("before") LocalDateTime before);
}
