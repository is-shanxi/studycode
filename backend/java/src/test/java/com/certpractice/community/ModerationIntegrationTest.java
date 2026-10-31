package com.certpractice.community;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.certpractice.identity.AdminAuditLog;
import com.certpractice.identity.AdminAuditLogRepository;
import com.certpractice.notification.Notification;
import com.certpractice.notification.NotificationRepository;
import com.certpractice.question.CorrectionReport;
import com.certpractice.question.CorrectionReportRepository;
import com.certpractice.question.QuestionDtos;
import com.certpractice.support.P1TestSupport;
import com.certpractice.support.P2TestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class ModerationIntegrationTest extends P2TestSupport {

    @Autowired
    private ContentReportRepository contentReports;

    @Autowired
    private CorrectionReportRepository correctionReports;

    @Autowired
    private DiscussionPostRepository discussionPosts;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private AdminAuditLogRepository auditLogs;

    @Test
    void reportResolutionHidesContentNotifiesReporterAndWritesAudit() throws Exception {
        P1TestSupport.SubjectChapter catalog = createSubjectAndChapter();
        P1TestSupport.QuestionRef question = createPublishedChoice(
                catalog.subjectId(), catalog.chapterId(), "Moderated discussion", "B", null);
        UUID authorId = registerUser();
        UUID reporterId = registerUser();
        UUID postId = createDiscussion(
                authorId, question.questionId(), question.versionId(),
                "需要处理的内容", "包含不当链接 http://bad.example 的帖子。");

        MvcResult reported = mockMvc.perform(post("/api/v1/discussions/{postId}/report", postId)
                        .with(appUser(reporterId, usernameFor(reporterId)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "包含垃圾链接"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID reportId = UUID.fromString(readJson(reported).get("reportId").asText());

        mockMvc.perform(post("/api/v1/discussions/{postId}/report", postId)
                        .with(appUser(reporterId, usernameFor(reporterId)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "重复举报"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(reportId.toString()));
        assertEquals(1, contentReports.findAll().stream()
                .filter(report -> report.getReporterUserId().equals(reporterId))
                .count());

        mockMvc.perform(get("/api/v1/admin/reports").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.reportId == '" + reportId + "')].source")
                        .value("CONTENT"));

        mockMvc.perform(post("/api/v1/admin/reports/{reportId}/resolve", reportId)
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "source", "CONTENT",
                                "resolution", "RESOLVED",
                                "moderationAction", "HIDE",
                                "note", "确认违规，隐藏帖子"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        ContentReport stored = contentReports.findById(reportId).orElseThrow();
        assertEquals(ContentReport.Status.RESOLVED, stored.getStatus());
        assertEquals(ADMIN_ID, stored.getHandledBy());
        assertEquals(DiscussionPost.Status.HIDDEN, discussionPosts.findById(postId).orElseThrow().getStatus());

        mockMvc.perform(get("/api/v1/discussions/{postId}", postId)
                        .with(appUser(reporterId, usernameFor(reporterId))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/questions/{questionId}/discussions", question.questionId())
                        .with(appUser(reporterId, usernameFor(reporterId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));

        assertTrue(notifications.findByRecipientUserIdOrderByCreatedAtDesc(reporterId, Pageable.unpaged())
                .getContent().stream()
                .anyMatch(notification -> notification.getType() == Notification.Type.MODERATION_RESULT));

        assertTrue(auditLogs.findAll().stream().anyMatch(log ->
                "CONTENT_REPORT_RESOLVE".equals(log.getAction())
                        && reportId.equals(log.getTargetId())
                        && log.getOutcome() == AdminAuditLog.Outcome.SUCCESS));
    }

    @Test
    void correctionReportTransitionsAndNotifiesReporter() throws Exception {
        P1TestSupport.SubjectChapter catalog = createSubjectAndChapter();
        P1TestSupport.QuestionRef question = createPublishedChoice(
                catalog.subjectId(), catalog.chapterId(), "Correction report question", "A", null);
        UUID reporterId = registerUser();

        MvcResult created = mockMvc.perform(post("/api/v1/correction-reports")
                        .with(appUser(reporterId, usernameFor(reporterId)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "questionId", question.questionId().toString(),
                                "questionVersionId", question.versionId().toString(),
                                "target", "STEM",
                                "description", "题干存在错别字"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();
        UUID reportId = UUID.fromString(readJson(created).get("reportId").asText());

        mockMvc.perform(post("/api/v1/admin/reports/{reportId}/resolve", reportId)
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "source", "CORRECTION",
                                "resolution", "RESOLVED",
                                "note", "已安排新版本修正"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        CorrectionReport stored = correctionReports.findById(reportId).orElseThrow();
        assertEquals(CorrectionReport.Status.RESOLVED, stored.getStatus());
        assertEquals(ADMIN_ID, stored.getHandledBy());

        assertTrue(notifications.findByRecipientUserIdOrderByCreatedAtDesc(reporterId, Pageable.unpaged())
                .getContent().stream()
                .anyMatch(notification -> notification.getType() == Notification.Type.MODERATION_RESULT));

        assertTrue(auditLogs.findAll().stream().anyMatch(log ->
                "CORRECTION_REPORT_RESOLVE".equals(log.getAction())
                        && reportId.equals(log.getTargetId())
                        && log.getOutcome() == AdminAuditLog.Outcome.SUCCESS));
    }

    @Test
    void correctionReportRejectsUnpublishedQuestion() throws Exception {
        P1TestSupport.SubjectChapter catalog = createSubjectAndChapter();
        UUID reporterId = registerUser();
        QuestionDtos.AdminQuestionDetail draft = authoringService.createQuestion(ADMIN_ID,
                new QuestionDtos.CreateQuestionRequest(
                        catalog.subjectId(), catalog.chapterId(), "SINGLE_CHOICE",
                        "draft correction " + UUID.randomUUID(),
                        "Explanation", null, "MEDIUM", "Test source",
                        List.of(
                                new QuestionDtos.OptionInput("A", "Option A", false),
                                new QuestionDtos.OptionInput("B", "Option B", true)),
                        List.of(), List.of()));

        mockMvc.perform(post("/api/v1/correction-reports")
                        .with(appUser(reporterId, usernameFor(reporterId)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "questionId", draft.questionId().toString(),
                                "questionVersionId", draft.versions().get(0).versionId().toString(),
                                "target", "STEM",
                                "description", "草稿不应接受纠错"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void ordinaryUsersCannotUseModerationEndpointsAndInvalidResolutionIsRejected() throws Exception {
        UUID userId = registerUser();
        mockMvc.perform(get("/api/v1/admin/reports")
                        .with(appUser(userId, usernameFor(userId))))
                .andExpect(status().isForbidden());

        P1TestSupport.SubjectChapter catalog = createSubjectAndChapter();
        P1TestSupport.QuestionRef question = createPublishedChoice(
                catalog.subjectId(), catalog.chapterId(), "Invalid resolution", "D", null);
        UUID postId = createDiscussion(
                userId, question.questionId(), question.versionId(), "待举报", "举报后使用非法状态处理。");
        MvcResult reported = mockMvc.perform(post("/api/v1/discussions/{postId}/report", postId)
                        .with(appUser(userId, usernameFor(userId)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "测试"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID reportId = UUID.fromString(readJson(reported).get("reportId").asText());

        mockMvc.perform(post("/api/v1/admin/reports/{reportId}/resolve", reportId)
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "source", "CONTENT",
                                "resolution", "OPEN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
