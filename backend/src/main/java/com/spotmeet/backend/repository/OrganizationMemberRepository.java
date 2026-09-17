package com.spotmeet.backend.repository;

import com.spotmeet.backend.model.OrganizationMember;
import com.spotmeet.backend.model.OrganizationMember.OrganizationRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, Long> {

    /** Finds the membership of a user in a specific organization. */
    Optional<OrganizationMember> findByUserIdAndOrganizationId(Long userId, Long organizationId);

    /** Lists all memberships of an organization (all members). */
    List<OrganizationMember> findByOrganizationId(Long organizationId);

    /** Lists memberships of a user (all organizations the user belongs to). */
    List<OrganizationMember> findByUserId(Long userId);

    /** Lists members of an organization with a specific role. */
    List<OrganizationMember> findByOrganizationIdAndRole(Long organizationId, OrganizationRole role);

    boolean existsByUserIdAndOrganizationId(Long userId, Long organizationId);
}
