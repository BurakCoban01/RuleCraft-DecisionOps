package com.rulecraft.decisionops.projects.persistence;

import com.rulecraft.decisionops.projects.domain.Project;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Optional<Project> findByIdAndTenantId(UUID id, UUID tenantId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id and p.tenantId = :tenantId")
    Optional<Project> lockByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
    boolean existsByTenantIdAndProjectKey(UUID tenantId, String projectKey);
    List<Project> findAllByTenantIdOrderByNameAsc(UUID tenantId);

    @Query("""
            select p from Project p join ProjectMembership m on m.projectId = p.id
            where p.tenantId = :tenantId and m.tenantId = :tenantId and m.userId = :userId
            order by p.name asc
            """)
    List<Project> findVisibleToMember(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);
}
