package com.certpractice.catalog;

import java.util.List;
import java.util.UUID;

/**
 * Catalog tree and tag payloads exposed to the practice client.
 */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CatalogTreeResponse(List<CertificateNode> certificates) {
    }

    public record CertificateNode(
            UUID id,
            String code,
            String name,
            List<SubjectNode> subjects) {
    }

    public record SubjectNode(
            UUID id,
            String code,
            String name,
            List<ChapterNode> chapters) {
    }

    public record ChapterNode(
            UUID id,
            String code,
            String name) {
    }

    public record TagResponse(
            UUID id,
            String name) {
    }
}
