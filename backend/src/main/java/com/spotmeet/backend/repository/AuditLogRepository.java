package com.spotmeet.backend.repository;

import com.spotmeet.backend.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** Action history of a specific user (most recent first). */
    List<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** Lookup by action type. */
    List<AuditLog> findByActionOrderByCreatedAtDesc(String action);

    /** Logs within a period, useful for LGPD audit reports. */
    @Query("SELECT l FROM AuditLog l WHERE l.createdAt BETWEEN :start AND :end ORDER BY l.createdAt DESC")
    List<AuditLog> findByPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** Most recent logs (last 100 actions). */
    List<AuditLog> findTop100ByOrderByCreatedAtDesc();

    /** Deletes every audit log except those of the preserved administrator. */
    @Modifying
    @Query("DELETE FROM AuditLog l WHERE l.user.id <> :adminId")
    void deleteAllExceptAdminLogs(@Param("adminId") Long adminId);
}
