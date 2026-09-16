package com.spotmeet.backend.repository;

import com.spotmeet.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Used by UserDetailsServiceImpl to load the user by e-mail (username). */
    Optional<User> findByEmail(String email);

    /** Checks existence by e-mail without loading the entity. */
    boolean existsByEmail(String email);

    /** Finds a user by the e-mail verification token. */
    Optional<User> findByVerificationToken(String verificationToken);

    /** Finds a user by the password recovery token. */
    Optional<User> findByRecoveryToken(String recoveryToken);

    /** Deletes every user except the preserved administrator. */
    @Modifying
    @Query("DELETE FROM User u WHERE u.id <> :adminId")
    void deleteAllExceptAdmin(@Param("adminId") Long adminId);
}
