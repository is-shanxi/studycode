package com.certpractice.catalog;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Administrator catalog maintenance payloads. Status is an explicit enum so
 * invalid values fail at the protocol boundary instead of being persisted.
 */
public final class CatalogAdminDtos {

    private CatalogAdminDtos() {
    }

    public record CertificateView(
            UUID id,
            String code,
            String name,
            String status,
            int sortOrder,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record SubjectView(
            UUID id,
            UUID certificateId,
            String code,
            String name,
            String status,
            int sortOrder,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ChapterView(
            UUID id,
            UUID subjectId,
            String code,
            String name,
            String status,
            int sortOrder,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record TagView(
            UUID id,
            String name,
            String normalizedName,
            String status,
            Instant createdAt) {
    }

    public record CertificateRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            CatalogStatus status,
            @Min(0) Integer sortOrder) {
    }

    public record SubjectCreateRequest(
            @NotNull UUID certificateId,
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            CatalogStatus status,
            @Min(0) Integer sortOrder) {
    }

    public record SubjectUpdateRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            CatalogStatus status,
            @Min(0) Integer sortOrder) {
    }

    public record ChapterCreateRequest(
            @NotNull UUID subjectId,
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            CatalogStatus status,
            @Min(0) Integer sortOrder) {
    }

    public record ChapterUpdateRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            CatalogStatus status,
            @Min(0) Integer sortOrder) {
    }

    public record TagRequest(
            @NotBlank @Size(max = 64) String name,
            CatalogStatus status) {
    }
}
