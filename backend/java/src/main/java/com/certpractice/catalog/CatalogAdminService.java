package com.certpractice.catalog;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.certpractice.identity.AdminAuditLog;
import com.certpractice.identity.AdminAuditService;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.text.TextNormalizer;
import com.certpractice.shared.web.Guard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrator maintenance for certificates, subjects, chapters and tags.
 * Entries are retired by setting INACTIVE; they are never physically deleted so
 * historical questions keep their catalog references.
 */
@Service
public class CatalogAdminService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9._-]{0,63}");

    private final CertificateRepository certificateRepository;
    private final SubjectRepository subjectRepository;
    private final ChapterRepository chapterRepository;
    private final TagRepository tagRepository;
    private final AdminAuditService audit;

    public CatalogAdminService(
            final CertificateRepository certificateRepository,
            final SubjectRepository subjectRepository,
            final ChapterRepository chapterRepository,
            final TagRepository tagRepository,
            final AdminAuditService audit) {
        this.certificateRepository = certificateRepository;
        this.subjectRepository = subjectRepository;
        this.chapterRepository = chapterRepository;
        this.tagRepository = tagRepository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CatalogAdminDtos.CertificateView> certificates() {
        return certificateRepository.findAllByOrderBySortOrderAscCodeAsc()
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogAdminDtos.SubjectView> subjects(final UUID certificateId) {
        List<Subject> entities;
        if (certificateId == null) {
            entities = subjectRepository.findAllByOrderByCertificateIdAscSortOrderAscCodeAsc();
        } else {
            requireCertificate(certificateId);
            entities = subjectRepository.findByCertificateIdOrderBySortOrderAsc(certificateId);
        }
        return entities.stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogAdminDtos.ChapterView> chapters(final UUID subjectId) {
        List<Chapter> entities;
        if (subjectId == null) {
            entities = chapterRepository.findAllByOrderBySubjectIdAscSortOrderAscCodeAsc();
        } else {
            requireSubject(subjectId);
            entities = chapterRepository.findBySubjectIdOrderBySortOrderAsc(subjectId);
        }
        return entities.stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogAdminDtos.TagView> tags() {
        return tagRepository.findAllByOrderByNameAsc()
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public CatalogAdminDtos.CertificateView createCertificate(
            final UUID adminId,
            final CatalogAdminDtos.CertificateRequest request) {
        String code = normalizeCode(request.code());
        String name = normalizeName(request.name(), 128);
        if (certificateRepository.existsByCode(code)) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "Certificate code already exists");
        }
        Certificate entity = new Certificate();
        entity.setCode(code);
        entity.setName(name);
        entity.setStatus(request.status() == null ? CatalogStatus.ACTIVE : request.status());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        certificateRepository.save(entity);
        audit.record(adminId, "CATALOG_CERTIFICATE_CREATE", "CERTIFICATE", entity.getId(),
                AdminAuditLog.Outcome.SUCCESS,
                Map.of("code", code, "status", entity.getStatus().name()));
        return toView(entity);
    }

    @Transactional
    public CatalogAdminDtos.CertificateView updateCertificate(
            final UUID adminId,
            final UUID certificateId,
            final CatalogAdminDtos.CertificateRequest request) {
        Certificate entity = requireCertificate(certificateId);
        String code = normalizeCode(request.code());
        if (certificateRepository.existsByCodeAndIdNot(code, certificateId)) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "Certificate code already exists");
        }
        entity.setCode(code);
        entity.setName(normalizeName(request.name(), 128));
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.sortOrder() != null) {
            entity.setSortOrder(request.sortOrder());
        }
        certificateRepository.save(entity);
        audit.record(adminId, "CATALOG_CERTIFICATE_UPDATE", "CERTIFICATE", certificateId,
                AdminAuditLog.Outcome.SUCCESS,
                Map.of("code", code, "status", entity.getStatus().name()));
        return toView(entity);
    }

    @Transactional
    public CatalogAdminDtos.SubjectView createSubject(
            final UUID adminId,
            final CatalogAdminDtos.SubjectCreateRequest request) {
        requireCertificate(request.certificateId());
        String code = normalizeCode(request.code());
        String name = normalizeName(request.name(), 128);
        if (subjectRepository.existsByCertificateIdAndCode(request.certificateId(), code)) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "Subject code already exists");
        }
        Subject entity = new Subject();
        entity.setCertificateId(request.certificateId());
        entity.setCode(code);
        entity.setName(name);
        entity.setStatus(request.status() == null ? CatalogStatus.ACTIVE : request.status());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        subjectRepository.save(entity);
        audit.record(adminId, "CATALOG_SUBJECT_CREATE", "SUBJECT", entity.getId(),
                AdminAuditLog.Outcome.SUCCESS,
                Map.of("code", code, "status", entity.getStatus().name()));
        return toView(entity);
    }

    @Transactional
    public CatalogAdminDtos.SubjectView updateSubject(
            final UUID adminId,
            final UUID subjectId,
            final CatalogAdminDtos.SubjectUpdateRequest request) {
        Subject entity = requireSubject(subjectId);
        String code = normalizeCode(request.code());
        if (subjectRepository.existsByCertificateIdAndCodeAndIdNot(
                entity.getCertificateId(), code, subjectId)) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "Subject code already exists");
        }
        entity.setCode(code);
        entity.setName(normalizeName(request.name(), 128));
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.sortOrder() != null) {
            entity.setSortOrder(request.sortOrder());
        }
        subjectRepository.save(entity);
        audit.record(adminId, "CATALOG_SUBJECT_UPDATE", "SUBJECT", subjectId,
                AdminAuditLog.Outcome.SUCCESS,
                Map.of("code", code, "status", entity.getStatus().name()));
        return toView(entity);
    }

    @Transactional
    public CatalogAdminDtos.ChapterView createChapter(
            final UUID adminId,
            final CatalogAdminDtos.ChapterCreateRequest request) {
        requireSubject(request.subjectId());
        String code = normalizeCode(request.code());
        String name = normalizeName(request.name(), 128);
        if (chapterRepository.existsBySubjectIdAndCode(request.subjectId(), code)) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "Chapter code already exists");
        }
        Chapter entity = new Chapter();
        entity.setSubjectId(request.subjectId());
        entity.setCode(code);
        entity.setName(name);
        entity.setStatus(request.status() == null ? CatalogStatus.ACTIVE : request.status());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        chapterRepository.save(entity);
        audit.record(adminId, "CATALOG_CHAPTER_CREATE", "CHAPTER", entity.getId(),
                AdminAuditLog.Outcome.SUCCESS,
                Map.of("code", code, "status", entity.getStatus().name()));
        return toView(entity);
    }

    @Transactional
    public CatalogAdminDtos.ChapterView updateChapter(
            final UUID adminId,
            final UUID chapterId,
            final CatalogAdminDtos.ChapterUpdateRequest request) {
        Chapter entity = requireChapter(chapterId);
        String code = normalizeCode(request.code());
        if (chapterRepository.existsBySubjectIdAndCodeAndIdNot(
                entity.getSubjectId(), code, chapterId)) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "Chapter code already exists");
        }
        entity.setCode(code);
        entity.setName(normalizeName(request.name(), 128));
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.sortOrder() != null) {
            entity.setSortOrder(request.sortOrder());
        }
        chapterRepository.save(entity);
        audit.record(adminId, "CATALOG_CHAPTER_UPDATE", "CHAPTER", chapterId,
                AdminAuditLog.Outcome.SUCCESS,
                Map.of("code", code, "status", entity.getStatus().name()));
        return toView(entity);
    }

    @Transactional
    public CatalogAdminDtos.TagView createTag(
            final UUID adminId,
            final CatalogAdminDtos.TagRequest request) {
        String name = normalizeName(request.name(), 64);
        String normalizedName = TextNormalizer.normalizeUsername(name);
        if (tagRepository.existsByNormalizedName(normalizedName)) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "Tag name already exists");
        }
        Tag entity = new Tag();
        entity.setName(name);
        entity.setNormalizedName(normalizedName);
        entity.setStatus(request.status() == null ? CatalogStatus.ACTIVE : request.status());
        tagRepository.save(entity);
        audit.record(adminId, "CATALOG_TAG_CREATE", "TAG", entity.getId(),
                AdminAuditLog.Outcome.SUCCESS,
                Map.of("name", name, "status", entity.getStatus().name()));
        return toView(entity);
    }

    @Transactional
    public CatalogAdminDtos.TagView updateTag(
            final UUID adminId,
            final UUID tagId,
            final CatalogAdminDtos.TagRequest request) {
        Tag entity = requireTag(tagId);
        String name = normalizeName(request.name(), 64);
        String normalizedName = TextNormalizer.normalizeUsername(name);
        if (tagRepository.existsByNormalizedNameAndIdNot(normalizedName, tagId)) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "Tag name already exists");
        }
        entity.setName(name);
        entity.setNormalizedName(normalizedName);
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        tagRepository.save(entity);
        audit.record(adminId, "CATALOG_TAG_UPDATE", "TAG", tagId,
                AdminAuditLog.Outcome.SUCCESS,
                Map.of("name", name, "status", entity.getStatus().name()));
        return toView(entity);
    }

    private Certificate requireCertificate(final UUID certificateId) {
        return certificateRepository.findById(certificateId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Certificate not found"));
    }

    private Subject requireSubject(final UUID subjectId) {
        return subjectRepository.findById(subjectId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Subject not found"));
    }

    private Chapter requireChapter(final UUID chapterId) {
        return chapterRepository.findById(chapterId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Chapter not found"));
    }

    private Tag requireTag(final UUID tagId) {
        return tagRepository.findById(tagId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Tag not found"));
    }

    private String normalizeCode(final String value) {
        String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        Guard.require(!code.isEmpty() && CODE_PATTERN.matcher(code).matches(),
                ErrorCode.VALIDATION_FAILED,
                "code must be 1-64 characters using letters, digits, dot, underscore or hyphen");
        return code;
    }

    private String normalizeName(final String value, final int maxLength) {
        String name = value == null ? "" : value.trim();
        Guard.require(!name.isEmpty(), ErrorCode.VALIDATION_FAILED, "name is required");
        Guard.require(name.length() <= maxLength, ErrorCode.VALIDATION_FAILED,
                "name must be at most " + maxLength + " characters");
        return name;
    }

    private CatalogAdminDtos.CertificateView toView(final Certificate entity) {
        return new CatalogAdminDtos.CertificateView(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus().name(),
                entity.getSortOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private CatalogAdminDtos.SubjectView toView(final Subject entity) {
        return new CatalogAdminDtos.SubjectView(
                entity.getId(),
                entity.getCertificateId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus().name(),
                entity.getSortOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private CatalogAdminDtos.ChapterView toView(final Chapter entity) {
        return new CatalogAdminDtos.ChapterView(
                entity.getId(),
                entity.getSubjectId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus().name(),
                entity.getSortOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private CatalogAdminDtos.TagView toView(final Tag entity) {
        return new CatalogAdminDtos.TagView(
                entity.getId(),
                entity.getName(),
                entity.getNormalizedName(),
                entity.getStatus().name(),
                entity.getCreatedAt());
    }
}
