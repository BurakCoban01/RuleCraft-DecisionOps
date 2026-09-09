package com.rulecraft.decisionops.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "tenant_role", nullable = false, length = 32)
    private TenantRole tenantRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected UserAccount() {
    }

    public UserAccount(UUID tenantId, String email, String displayName, String passwordHash,
            TenantRole tenantRole, Instant now) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.email = normalizeEmail(email);
        this.displayName = displayName.strip();
        this.passwordHash = passwordHash;
        this.tenantRole = tenantRole;
        this.status = AccountStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public TenantRole getTenantRole() {
        return tenantRole;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public void changePassword(String encodedPassword, Instant now) {
        this.passwordHash = encodedPassword;
        this.updatedAt = now;
    }

    public void changeStatus(AccountStatus nextStatus, Instant now) {
        this.status = nextStatus;
        this.updatedAt = now;
    }
}
