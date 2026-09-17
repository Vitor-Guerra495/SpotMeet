package com.spotmeet.backend.repository;

import com.spotmeet.backend.model.AccessRequest;
import com.spotmeet.backend.model.AccessRequest.AccessRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AccessRequest repository. Uses derived JPA queries only.
 */
@Repository
public interface AccessRequestRepository extends JpaRepository<AccessRequest, Long> {

    /** Lists requests of an organization filtered by status. */
    List<AccessRequest> findByOrganizationIdAndStatus(Long organizationId, AccessRequestStatus status);

    /** Checks whether the user has a request in the given status for the organization. */
    boolean existsByUserIdAndOrganizationIdAndStatus(Long userId, Long organizationId, AccessRequestStatus status);

    /** Finds the user's requests for the organization (list for safety against duplicates). */
    List<AccessRequest> findByUserIdAndOrganizationId(Long userId, Long organizationId);

    /** Lists all requests of a user. */
    List<AccessRequest> findByUserId(Long userId);

    List<AccessRequest> findByOrganizationId(Long organizationId);
}
