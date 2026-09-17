package com.spotmeet.backend.repository;

import com.spotmeet.backend.model.CommitteeMember;
import com.spotmeet.backend.model.CommitteeMember.CommitteeMembershipStatus;
import com.spotmeet.backend.model.CommitteeMember.CommitteeRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommitteeMemberRepository extends JpaRepository<CommitteeMember, Long> {

    Optional<CommitteeMember> findByUserIdAndCommitteeId(Long userId, Long committeeId);

    boolean existsByUserIdAndCommitteeId(Long userId, Long committeeId);

    boolean existsByUserIdAndCommitteeIdAndStatus(Long userId, Long committeeId, CommitteeMembershipStatus status);

    List<CommitteeMember> findByCommitteeId(Long committeeId);

    List<CommitteeMember> findByCommitteeIdAndStatus(Long committeeId, CommitteeMembershipStatus status);

    List<CommitteeMember> findByCommitteeIdAndRole(Long committeeId, CommitteeRole role);

    List<CommitteeMember> findByUserId(Long userId);

    List<CommitteeMember> findByUserIdAndStatus(Long userId, CommitteeMembershipStatus status);

    List<CommitteeMember> findByUserIdAndCommitteeOrganizationId(Long userId, Long organizationId);

    List<CommitteeMember> findByUserIdAndCommitteeOrganizationIdAndStatus(Long userId, Long organizationId, CommitteeMembershipStatus status);

    List<CommitteeMember> findByCommitteeOrganizationIdAndStatus(Long organizationId, CommitteeMembershipStatus status);

    long countByCommitteeIdAndStatus(Long committeeId, CommitteeMembershipStatus status);
}
