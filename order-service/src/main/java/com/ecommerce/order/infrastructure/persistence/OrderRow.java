package com.ecommerce.order.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Phase 1 walking skeleton row mapping for the {@code orders} table.
 *
 * <p>A null {@link #version} marks the entity as new, so Spring Data R2DBC issues an INSERT;
 * a non-null version triggers an optimistic-locking UPDATE (ADR-05). The rich domain aggregate
 * and its mapper replace this minimal row in Phase 2/4.
 */
@Table("orders")
public class OrderRow {

    @Id
    private UUID id;

    @Column("customer_id")
    private String customerId;

    private String status;

    @Version
    private Long version;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
