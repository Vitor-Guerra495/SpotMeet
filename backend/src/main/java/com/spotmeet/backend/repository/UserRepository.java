package com.spotmeet.backend.repository;

import com.spotmeet.backend.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Used by UserDetailsServiceImpl to load the user by e-mail (username). */
    Optional<User> findByEmail(String email);

    /** Checks existence by e-mail without loading the entity. */
    boolean existsByEmail(String email);

    /** Serializes code replacement and consumption for this account. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    /**
     * Confirmed regular users whose name or e-mail contains the term (already escaped with '!'),
     * leaving out the owner, the members and anyone with a pending invitation to the organization.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.emailVerified = true AND u.role = 'USER' AND u.id <> :ownerId
              AND (LOWER(u.name) LIKE LOWER(CONCAT('%', :term, '%')) ESCAPE '!'
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :term, '%')) ESCAPE '!')
              AND NOT EXISTS (SELECT 1 FROM OrganizationMember m
                              WHERE m.user.id = u.id AND m.organization.id = :organizationId)
              AND NOT EXISTS (SELECT 1 FROM OrganizationInvitation i
                              WHERE i.user.id = u.id AND i.organization.id = :organizationId
                                AND i.status = com.spotmeet.backend.model.OrganizationInvitation.InvitationStatus.PENDING
                                AND i.expiresAt > :now)
            ORDER BY u.name, u.email
            """)
    List<User> findInviteCandidates(@Param("term") String term, @Param("organizationId") Long organizationId,
                                    @Param("ownerId") Long ownerId, @Param("now") Instant now, Pageable pageable);

    /** Deletes every user except the preserved administrator. */
    @Modifying
    @Query("DELETE FROM User u WHERE u.id <> :adminId")
    void deleteAllExceptAdmin(@Param("adminId") Long adminId);
}
