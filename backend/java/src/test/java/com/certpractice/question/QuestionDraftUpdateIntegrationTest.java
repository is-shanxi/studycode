package com.certpractice.question;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.certpractice.support.P1TestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Draft updates replace options, aliases and tags. Hibernate flushes inserts
 * before deletes, so the delete must be flushed explicitly or re-inserting the
 * same correct option / normalized alias / version tag violates a unique index.
 */
class QuestionDraftUpdateIntegrationTest extends P1TestSupport {

    @Test
    void updatingDraftOptionsAndTagsReplacesContentWithoutUniqueConflicts() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        UUID tagId = createTag("draft-update-" + UUID.randomUUID());
        QuestionDtos.AdminQuestionDetail detail = authoringService.createQuestion(ADMIN_ID,
                new QuestionDtos.CreateQuestionRequest(
                        catalog.subjectId(), catalog.chapterId(), "SINGLE_CHOICE",
                        "draft update choice " + UUID.randomUUID(),
                        "Explanation", null, "MEDIUM", "Test source",
                        List.of(
                                new QuestionDtos.OptionInput("A", "Option A", false),
                                new QuestionDtos.OptionInput("B", "Option B", true)),
                        List.of(), List.of(tagId)));
        UUID versionId = detail.versions().get(0).versionId();
        String updatedStem = "updated choice " + UUID.randomUUID();

        Map<String, Object> update = Map.of(
                "stem", updatedStem,
                "options", List.of(
                        Map.of("key", "A", "content", "Option A v2", "correct", false),
                        Map.of("key", "B", "content", "Option B v2", "correct", true)),
                "tagIds", List.of(tagId.toString()));

        mockMvc.perform(put("/api/v1/admin/question-versions/{id}", versionId)
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions[0].stem").value(updatedStem))
                .andExpect(jsonPath("$.versions[0].options[1].content").value("Option B v2"));
    }

    @Test
    void updatingFillDraftAliasesAndTagsReplacesContentWithoutUniqueConflicts() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        UUID tagId = createTag("draft-alias-" + UUID.randomUUID());
        QuestionDtos.AdminQuestionDetail detail = authoringService.createQuestion(ADMIN_ID,
                new QuestionDtos.CreateQuestionRequest(
                        catalog.subjectId(), catalog.chapterId(), "SINGLE_BLANK",
                        "draft update blank " + UUID.randomUUID(),
                        "Explanation", "SYN", "MEDIUM", "Test source",
                        List.of(), List.of("同步"), List.of(tagId)));
        UUID versionId = detail.versions().get(0).versionId();
        String updatedStem = "updated blank " + UUID.randomUUID();

        Map<String, Object> update = Map.of(
                "stem", updatedStem,
                "aliases", List.of("同步"),
                "tagIds", List.of(tagId.toString()));

        mockMvc.perform(put("/api/v1/admin/question-versions/{id}", versionId)
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions[0].stem").value(updatedStem))
                .andExpect(jsonPath("$.versions[0].aliases[0]").value("同步"));
    }
}
