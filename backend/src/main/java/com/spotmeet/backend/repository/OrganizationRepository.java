package com.spotmeet.backend.repository;

import com.spotmeet.backend.model.Organization;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Organization repository.
 */
@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    /** Keeps membership and organization writes in the same lock order. */
    default Optional<Organization> findByIdForUpdate(Long id) {
        lockForWrite();
        return findLockedById(id);
    }

    // Reserve write access before locking a row to avoid a later lock upgrade.
    @Modifying
    @Query(value = "LOCK TABLE organizations IN ROW EXCLUSIVE MODE", nativeQuery = true)
    void lockForWrite();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Organization o WHERE o.id = :id")
    Optional<Organization> findLockedById(@Param("id") Long id);

    /** Serializes uniqueness checks and writes without adding an index to legacy data. */
    @Modifying
    @Query(value = "LOCK TABLE organizations IN SHARE ROW EXCLUSIVE MODE", nativeQuery = true)
    void lockForUniquenessCheck();

    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM organizations
                WHERE lower(btrim(regexp_replace(name, :spaces, ' ', 'g'))) = lower(:name)
                  AND (CAST(:excludedId AS bigint) IS NULL OR id <> :excludedId))
            """, nativeQuery = true)
    boolean existsByNormalizedName(@Param("name") String name, @Param("spaces") String spaces, @Param("excludedId") Long excludedId);

    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM organizations
                WHERE lower(btrim(regexp_replace(access_key, :spaces, ' ', 'g'))) = lower(:key)
                  AND (CAST(:excludedId AS bigint) IS NULL OR id <> :excludedId))
            """, nativeQuery = true)
    boolean existsByNormalizedAccessKey(@Param("key") String accessKey, @Param("spaces") String spaces, @Param("excludedId") Long excludedId);

    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM organizations WHERE cnpj = :cnpj
                AND (CAST(:excludedId AS bigint) IS NULL OR id <> :excludedId))
            """, nativeQuery = true)
    boolean existsByCnpjExcludingId(@Param("cnpj") String cnpj, @Param("excludedId") Long excludedId);

    /** At most two matches are enough to detect an ambiguous legacy key. */
    @Query(value = """
            SELECT id FROM organizations
                WHERE lower(btrim(regexp_replace(access_key, :spaces, ' ', 'g'))) = lower(:key)
                ORDER BY id LIMIT 2
            """, nativeQuery = true)
    List<Long> findIdsByAccessKeyIgnoreCase(@Param("key") String accessKey, @Param("spaces") String spaces);

    /** Loads all organizations with the owner fetched in the same query (SysAdmin listing). */
    @Query("SELECT o FROM Organization o LEFT JOIN FETCH o.owner")
    List<Organization> findAllWithOwner();
}
