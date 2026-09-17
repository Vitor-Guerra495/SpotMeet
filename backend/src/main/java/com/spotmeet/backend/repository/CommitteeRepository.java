package com.spotmeet.backend.repository;

import com.spotmeet.backend.model.Committee;
import com.spotmeet.backend.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommitteeRepository extends JpaRepository<Committee, Long> {

    List<Committee> findByOrganization(Organization organization);

    List<Committee> findByOrganizationId(Long organizationId);

    List<Committee> findByOrganizationIdOrderByNameAsc(Long organizationId);

    Optional<Committee> findByIdAndOrganizationId(Long id, Long organizationId);

    boolean existsByOrganizationIdAndName(Long organizationId, String name);

    Optional<Committee> findByOrganizationIdAndName(Long organizationId, String name);
}
