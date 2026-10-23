package com.spotmeet.backend.repository;

import com.spotmeet.backend.model.OrganizationInvitation;
import com.spotmeet.backend.model.OrganizationInvitation.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrganizationInvitationRepository extends JpaRepository<OrganizationInvitation, Long> {
    Optional<OrganizationInvitation> findByOrganizationIdAndUserId(Long organizationId, Long userId);

    // Read the destination without caching the invitation before the organization lock.
    @Query("SELECT i.organization.id FROM OrganizationInvitation i WHERE i.id = :id AND i.user.id = :userId")
    Optional<Long> findOrganizationIdForRecipient(@Param("id") Long id, @Param("userId") Long userId);

    @Query("""
            SELECT i FROM OrganizationInvitation i
            JOIN FETCH i.organization JOIN FETCH i.invitedBy JOIN FETCH i.user
            WHERE i.user.id = :userId AND i.status = :status
              AND i.expiresAt > :now AND i.organization.approved = true
              AND NOT EXISTS (SELECT m.id FROM OrganizationMember m
                              WHERE m.user.id = :userId AND m.organization.id = i.organization.id)
            ORDER BY i.createdAt DESC, i.id DESC
            """)
    List<OrganizationInvitation> findPendingForRecipient(@Param("userId") Long userId,
            @Param("status") InvitationStatus status, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM OrganizationInvitation i WHERE i.organization.id = :organizationId")
    void deleteByOrganizationId(@Param("organizationId") Long organizationId);

    @Modifying
    @Query("DELETE FROM OrganizationInvitation i WHERE i.organization.id = :organizationId AND i.user.id = :userId")
    void deleteByOrganizationIdAndUserId(@Param("organizationId") Long organizationId, @Param("userId") Long userId);
}
