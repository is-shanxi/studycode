package com.certpractice.catalog;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only catalog queries. Only active catalog entries are offered to users.
 */
@Service
public class CatalogService {

    private final CertificateRepository certificates;
    private final SubjectRepository subjects;
    private final ChapterRepository chapters;
    private final TagRepository tags;

    public CatalogService(
            final CertificateRepository certificates,
            final SubjectRepository subjects,
            final ChapterRepository chapters,
            final TagRepository tags) {
        this.certificates = certificates;
        this.subjects = subjects;
        this.chapters = chapters;
        this.tags = tags;
    }

    @Transactional(readOnly = true)
    public CatalogDtos.CatalogTreeResponse tree() {
        List<CatalogDtos.CertificateNode> nodes = certificates
                .findByStatusOrderBySortOrderAsc(CatalogStatus.ACTIVE)
                .stream()
                .map(certificate -> new CatalogDtos.CertificateNode(
                        certificate.getId(),
                        certificate.getCode(),
                        certificate.getName(),
                        subjects.findByCertificateIdOrderBySortOrderAsc(certificate.getId())
                                .stream()
                                .filter(subject -> subject.getStatus() == CatalogStatus.ACTIVE)
                                .map(subject -> new CatalogDtos.SubjectNode(
                                        subject.getId(),
                                        subject.getCode(),
                                        subject.getName(),
                                        chapters.findBySubjectIdOrderBySortOrderAsc(subject.getId())
                                                .stream()
                                                .filter(chapter -> chapter.getStatus() == CatalogStatus.ACTIVE)
                                                .map(chapter -> new CatalogDtos.ChapterNode(
                                                        chapter.getId(),
                                                        chapter.getCode(),
                                                        chapter.getName()))
                                                .toList()))
                                .toList()))
                .toList();
        return new CatalogDtos.CatalogTreeResponse(nodes);
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.TagResponse> tags() {
        return tags.findByStatusOrderByNameAsc(CatalogStatus.ACTIVE)
                .stream()
                .map(tag -> new CatalogDtos.TagResponse(tag.getId(), tag.getName()))
                .toList();
    }
}
