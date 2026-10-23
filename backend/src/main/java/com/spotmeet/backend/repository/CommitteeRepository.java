package com.spotmeet.backend.repository;

import com.spotmeet.backend.model.Committee;
import com.spotmeet.backend.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommitteeRepository extends JpaRepository<Committee, Long> {

    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM committees
                WHERE organization_id = :organizationId
                  AND lower(btrim(regexp_replace(name, :spaces, ' ', 'g'))) = lower(:name)
                  AND (CAST(:excludedId AS bigint) IS NULL OR id <> :excludedId))
            """, nativeQuery = true)
    boolean existsByNormalizedName(@Param("organizationId") Long organizationId, @Param("name") String name,
            @Param("spaces") String spaces, @Param("excludedId") Long excludedId);

    List<Committee> findByOrganization(Organization organization);

    List<Committee> findByOrganizationId(Long organizationId);

    List<Committee> findByOrganizationIdOrderByNameAsc(Long organizationId);

    Optional<Committee> findByIdAndOrganizationId(Long id, Long organizationId);

    boolean existsByOrganizationIdAndName(Long organizationId, String name);

    Optional<Committee> findByOrganizationIdAndName(Long organizationId, String name);
}
