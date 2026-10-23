package com.spotmeet.backend.repository;

import com.spotmeet.backend.model.UserProfilePhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UserProfilePhotoRepository extends JpaRepository<UserProfilePhoto, Long> {
    @Query("SELECT p FROM UserProfilePhoto p WHERE p.user.id = :userId")
    Optional<UserProfilePhoto> findByUserId(@Param("userId") Long userId);
}
