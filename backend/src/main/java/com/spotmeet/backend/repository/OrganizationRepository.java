package com.spotmeet.backend.repository;

import com.spotmeet.backend.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Organization repository.
 *
 * Case-sensitivity note: findByAccessKey() generates "WHERE access_key = :accessKey".
 * PostgreSQL compares with the column collation (case-sensitive by default),
 * so "#Minerva" and "#minerva" are different keys.
 */
@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    /** Finds an organization by exact access key (case-sensitive by design). */
    Optional<Organization> findByAccessKey(String accessKey);

    /** Checks key uniqueness before creating a new organization. */
    boolean existsByAccessKey(String accessKey);

    /** Loads all organizations with the owner fetched in the same query (SysAdmin listing). */
    @Query("SELECT o FROM Organization o LEFT JOIN FETCH o.owner")
    List<Organization> findAllWithOwner();
}
