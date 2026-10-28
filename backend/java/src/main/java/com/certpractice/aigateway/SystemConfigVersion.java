package com.certpractice.aigateway;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "system_config_version")
public class SystemConfigVersion {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "config_key", nullable = false, length = 96)
    private String configKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_value_json", nullable = false, columnDefinition = "jsonb")
    private String configValueJson;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @CreationTimestamp
    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
