package com.rulecraft.decisionops.identity.persistence;

import com.rulecraft.decisionops.identity.domain.UserAccount;
import com.rulecraft.decisionops.identity.domain.AccountStatus;
import com.rulecraft.decisionops.identity.domain.TenantRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    @Query("""
            select u from UserAccount u, Tenant t
            where u.tenantId = t.id and t.slug = :tenantSlug and u.email = :email
            """)
    Optional<UserAccount> findForLogin(@Param("tenantSlug") String tenantSlug, @Param("email") String email);

    Optional<UserAccount> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);

    List<UserAccount> findAllByTenantIdOrderByDisplayNameAsc(UUID tenantId);

    long countByTenantIdAndTenantRoleAndStatus(UUID tenantId, TenantRole tenantRole, AccountStatus status);
}
