package com.certpractice.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.certpractice.catalog.CatalogStatus;
import com.certpractice.catalog.Certificate;
import com.certpractice.catalog.CertificateRepository;
import com.certpractice.catalog.Chapter;
import com.certpractice.catalog.ChapterRepository;
import com.certpractice.catalog.Subject;
import com.certpractice.catalog.SubjectRepository;
import com.certpractice.catalog.Tag;
import com.certpractice.catalog.TagRepository;
import com.certpractice.question.QuestionAuthoringService;
import com.certpractice.question.QuestionDtos;
import com.certpractice.question.QuestionLifecycleService;
import com.certpractice.shared.security.Role;
import com.certpractice.shared.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Shared fixtures for P1 integration tests. Each test creates its own catalog
 * and questions so tests do not depend on execution order.
 */
public abstract class P1TestSupport extends IntegrationTestSupport {

    protected static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    protected static final UUID STUDENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected CertificateRepository certificates;

    @Autowired
    protected SubjectRepository subjects;

    @Autowired
    protected ChapterRepository chapters;

    @Autowired
    protected TagRepository tags;

    @Autowired
    protected QuestionAuthoringService authoringService;

    @Autowired
    protected QuestionLifecycleService lifecycleService;

    protected RequestPostProcessor admin() {
        return user(UserPrincipal.of(ADMIN_ID, "admin", Set.of(Role.ADMIN), true));
    }

    protected RequestPostProcessor appUser(final UUID userId, final String username) {
        return user(UserPrincipal.of(userId, username, Set.of(Role.USER), true));
    }

    protected SubjectChapter createSubjectAndChapter() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Certificate certificate = new Certificate();
        certificate.setCode("CERT-" + suffix);
        certificate.setName("Test Certificate " + suffix);
        certificate.setStatus(CatalogStatus.ACTIVE);
        certificates.save(certificate);

        Subject subject = new Subject();
        subject.setCertificateId(certificate.getId());
        subject.setCode("SUB-" + suffix);
        subject.setName("Test Subject " + suffix);
        subject.setStatus(CatalogStatus.ACTIVE);
        subjects.save(subject);

        Chapter chapter = new Chapter();
        chapter.setSubjectId(subject.getId());
        chapter.setCode("CH-" + suffix);
        chapter.setName("Test Chapter " + suffix);
        chapter.setStatus(CatalogStatus.ACTIVE);
        chapters.save(chapter);

        return new SubjectChapter(subject.getId(), chapter.getId());
    }

    protected UUID createTag(final String name) {
        Tag tag = new Tag();
        tag.setName(name);
        tag.setNormalizedName(name.toLowerCase());
        tag.setStatus(CatalogStatus.ACTIVE);
        tags.save(tag);
        return tag.getId();
    }

    protected QuestionRef createPublishedChoice(
            final UUID subjectId,
            final UUID chapterId,
            final String stem,
            final String correctKey,
            final List<UUID> tagIds) {
        List<QuestionDtos.OptionInput> options = List.of(
                new QuestionDtos.OptionInput("A", "A option for " + stem, "A".equals(correctKey)),
                new QuestionDtos.OptionInput("B", "B option for " + stem, "B".equals(correctKey)),
                new QuestionDtos.OptionInput("C", "C option for " + stem, "C".equals(correctKey)),
                new QuestionDtos.OptionInput("D", "D option for " + stem, "D".equals(correctKey)));
        QuestionDtos.AdminQuestionDetail detail = authoringService.createQuestion(ADMIN_ID,
                new QuestionDtos.CreateQuestionRequest(
                        subjectId, chapterId, "SINGLE_CHOICE", stem,
                        "Explanation for " + stem, null, "MEDIUM", "Test source",
                        options, List.of(), tagIds));
        UUID versionId = detail.versions().get(0).versionId();
        publish(versionId);
        return new QuestionRef(detail.questionId(), versionId);
    }

    protected QuestionRef createPublishedBlank(
            final UUID subjectId,
            final UUID chapterId,
            final String stem,
            final String referenceAnswer,
            final List<String> aliases,
            final List<UUID> tagIds) {
        QuestionDtos.AdminQuestionDetail detail = authoringService.createQuestion(ADMIN_ID,
                new QuestionDtos.CreateQuestionRequest(
                        subjectId, chapterId, "SINGLE_BLANK", stem,
                        "Explanation for " + stem, referenceAnswer, "EASY", "Test source",
                        List.of(), aliases, tagIds));
        UUID versionId = detail.versions().get(0).versionId();
        publish(versionId);
        return new QuestionRef(detail.questionId(), versionId);
    }

    protected void publish(final UUID versionId) {
        lifecycleService.submitReview(ADMIN_ID, versionId, null);
        lifecycleService.approve(ADMIN_ID, versionId, new QuestionDtos.ReviewRequest(null, true));
    }

    protected UUID registerUser() throws Exception {
        String username = "user" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<String, String> payload = Map.of(
                "username", username,
                "password", "Password@123",
                "passwordConfirmation", "Password@123");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(
                result.getResponse().getContentAsString()).get("userId").asText());
    }

    public record SubjectChapter(UUID subjectId, UUID chapterId) {
    }

    public record QuestionRef(UUID questionId, UUID versionId) {
    }
}
