package com.certpractice.catalog;

import java.util.List;
import java.util.UUID;

import com.certpractice.shared.security.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrator maintenance of certificate, subject, chapter and tag records.
 * The security layer requires ROLE_ADMIN for /api/v1/admin/**; each handler
 * re-checks the role before delegating.
 */
@RestController
@RequestMapping("/api/v1/admin/catalog")
public class CatalogAdminController {

    private final CatalogAdminService catalogAdminService;
    private final CurrentUserService currentUserService;

    public CatalogAdminController(
            final CatalogAdminService catalogAdminService,
            final CurrentUserService currentUserService) {
        this.catalogAdminService = catalogAdminService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/certificates")
    public List<CatalogAdminDtos.CertificateView> certificates() {
        currentUserService.requireAdmin();
        return catalogAdminService.certificates();
    }

    @PostMapping("/certificates")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogAdminDtos.CertificateView createCertificate(
            @Valid @RequestBody final CatalogAdminDtos.CertificateRequest request) {
        currentUserService.requireAdmin();
        return catalogAdminService.createCertificate(currentUserService.requireUserId(), request);
    }

    @PutMapping("/certificates/{certificateId}")
    public CatalogAdminDtos.CertificateView updateCertificate(
            @PathVariable final UUID certificateId,
            @Valid @RequestBody final CatalogAdminDtos.CertificateRequest request) {
        currentUserService.requireAdmin();
        return catalogAdminService.updateCertificate(
                currentUserService.requireUserId(), certificateId, request);
    }

    @GetMapping("/subjects")
    public List<CatalogAdminDtos.SubjectView> subjects(
            @RequestParam(required = false) final UUID certificateId) {
        currentUserService.requireAdmin();
        return catalogAdminService.subjects(certificateId);
    }

    @PostMapping("/subjects")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogAdminDtos.SubjectView createSubject(
            @Valid @RequestBody final CatalogAdminDtos.SubjectCreateRequest request) {
        currentUserService.requireAdmin();
        return catalogAdminService.createSubject(currentUserService.requireUserId(), request);
    }

    @PutMapping("/subjects/{subjectId}")
    public CatalogAdminDtos.SubjectView updateSubject(
            @PathVariable final UUID subjectId,
            @Valid @RequestBody final CatalogAdminDtos.SubjectUpdateRequest request) {
        currentUserService.requireAdmin();
        return catalogAdminService.updateSubject(
                currentUserService.requireUserId(), subjectId, request);
    }

    @GetMapping("/chapters")
    public List<CatalogAdminDtos.ChapterView> chapters(
            @RequestParam(required = false) final UUID subjectId) {
        currentUserService.requireAdmin();
        return catalogAdminService.chapters(subjectId);
    }

    @PostMapping("/chapters")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogAdminDtos.ChapterView createChapter(
            @Valid @RequestBody final CatalogAdminDtos.ChapterCreateRequest request) {
        currentUserService.requireAdmin();
        return catalogAdminService.createChapter(currentUserService.requireUserId(), request);
    }

    @PutMapping("/chapters/{chapterId}")
    public CatalogAdminDtos.ChapterView updateChapter(
            @PathVariable final UUID chapterId,
            @Valid @RequestBody final CatalogAdminDtos.ChapterUpdateRequest request) {
        currentUserService.requireAdmin();
        return catalogAdminService.updateChapter(
                currentUserService.requireUserId(), chapterId, request);
    }

    @GetMapping("/tags")
    public List<CatalogAdminDtos.TagView> tags() {
        currentUserService.requireAdmin();
        return catalogAdminService.tags();
    }

    @PostMapping("/tags")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogAdminDtos.TagView createTag(
            @Valid @RequestBody final CatalogAdminDtos.TagRequest request) {
        currentUserService.requireAdmin();
        return catalogAdminService.createTag(currentUserService.requireUserId(), request);
    }

    @PutMapping("/tags/{tagId}")
    public CatalogAdminDtos.TagView updateTag(
            @PathVariable final UUID tagId,
            @Valid @RequestBody final CatalogAdminDtos.TagRequest request) {
        currentUserService.requireAdmin();
        return catalogAdminService.updateTag(
                currentUserService.requireUserId(), tagId, request);
    }
}
