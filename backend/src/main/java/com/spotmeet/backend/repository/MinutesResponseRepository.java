package com.spotmeet.backend.repository;

import com.spotmeet.backend.model.MinutesResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MinutesResponseRepository extends JpaRepository<MinutesResponse, Long> {

    List<MinutesResponse> findByMinutesIdOrderByRespondedAtAsc(Long minutesId);

    List<MinutesResponse> findByMinutesIdInAndUserId(Collection<Long> minutesIds, Long userId);

    boolean existsByMinutesIdAndUserId(Long minutesId, Long userId);

    @Modifying
    @Query("DELETE FROM MinutesResponse r WHERE r.minutes.id = :minutesId")
    void deleteByMinutesId(@Param("minutesId") Long minutesId);
}
