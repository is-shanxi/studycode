package com.certpractice.community;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.certpractice.practice.PracticeSession;
import com.certpractice.question.QuestionDtos;
import com.certpractice.support.P1TestSupport;
import com.certpractice.support.P2TestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class DiscussionIntegrationTest extends P2TestSupport {

    @Test
    void discussionsAreVersionBoundAndCommentsStayOneLevelDeep() throws Exception {
        P1TestSupport.SubjectChapter catalog = createSubjectAndChapter();
        P1TestSupport.QuestionRef question = createPublishedChoice(
                catalog.subjectId(), catalog.chapterId(), "Version bound discussion", "B", null);
        UUID authorId = registerUser();
        UUID replierId = registerUser();

        UUID postId = createDiscussion(
                authorId, question.questionId(), question.versionId(),
                "这道题怎么理解？", "题干中的传输层如何判断？");

        UUID topLevelId = createComment(replierId, postId, null, null, "先看 TCP 的职责。");
        UUID replyId = createComment(authorId, postId, topLevelId, null, "明白了，谢谢。");
        createComment(authorId, postId, topLevelId, replyId, "引用这条回复补充一下。");

        Map<String, Object> nestedPayload = new HashMap<>();
        nestedPayload.put("parentCommentId", replyId.toString());
        nestedPayload.put("quotedCommentId", replyId.toString());
        nestedPayload.put("body", "不允许的第三层回复");
        mockMvc.perform(post("/api/v1/discussions/{postId}/comments", postId)
                        .with(appUser(replierId, usernameFor(replierId)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nestedPayload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

        mockMvc.perform(get("/api/v1/discussions/{postId}", postId)
                        .with(appUser(authorId, usernameFor(authorId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(postId.toString()))
                .andExpect(jsonPath("$.questionId").value(question.questionId().toString()))
                .andExpect(jsonPath("$.questionVersionId").value(question.versionId().toString()))
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.versionCurrent").value(true))
                .andExpect(jsonPath("$.comments.length()").value(3))
                .andExpect(jsonPath("$.comments[1].parentCommentId").value(topLevelId.toString()))
                .andExpect(jsonPath("$.comments[2].quotedCommentId").value(replyId.toString()));

        mockMvc.perform(get("/api/v1/questions/{questionId}/discussions", question.questionId())
                        .with(appUser(authorId, usernameFor(authorId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].commentCount").value(3))
                .andExpect(jsonPath("$.items[0].versionNo").value(1));
    }

    @Test
    void quotedRootCommentsMustStayWithinTheSelectedThread() throws Exception {
        P1TestSupport.SubjectChapter catalog = createSubjectAndChapter();
        P1TestSupport.QuestionRef question = createPublishedChoice(
                catalog.subjectId(), catalog.chapterId(), "Quote thread validation", "A", null);
        UUID authorId = registerUser();
        UUID postId = createDiscussion(
                authorId, question.questionId(), question.versionId(),
                "引用校验", "引用根评论时也必须保持同一线程。");

        UUID rootA = createComment(authorId, postId, null, null, "根评论 A");
        UUID rootB = createComment(authorId, postId, null, null, "根评论 B");

        Map<String, Object> crossRootPayload = new HashMap<>();
        crossRootPayload.put("parentCommentId", rootA.toString());
        crossRootPayload.put("quotedCommentId", rootB.toString());
        crossRootPayload.put("body", "跨根引用应被拒绝");
        mockMvc.perform(post("/api/v1/discussions/{postId}/comments", postId)
                        .with(appUser(authorId, usernameFor(authorId)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crossRootPayload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

        createComment(authorId, postId, rootB, rootB, "同线程引用根评论");
        createComment(authorId, postId, null, rootB, "未指定 parent 时推导为根评论");
    }

    @Test
    void inProgressPracticeBlocksDiscussionAccessUntilSessionCompletes() throws Exception {
        P1TestSupport.SubjectChapter catalog = createSubjectAndChapter();
        P1TestSupport.QuestionRef question = createPublishedChoice(
                catalog.subjectId(), catalog.chapterId(), "In progress discussion", "A", null);
        UUID authorId = registerUser();
        UUID postId = createDiscussion(
                authorId, question.questionId(), question.versionId(),
                "进行中不可见", "这条讨论应在练习完成后可见。");

        PracticeSession session = createInProgressSession(
                authorId, question.questionId(), question.versionId());

        mockMvc.perform(get("/api/v1/questions/{questionId}/discussions", question.questionId())
                        .with(appUser(authorId, usernameFor(authorId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

        mockMvc.perform(get("/api/v1/discussions/{postId}", postId)
                        .with(appUser(authorId, usernameFor(authorId))))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/questions/{questionId}/discussions", question.questionId())
                        .with(appUser(authorId, usernameFor(authorId)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "questionVersionId", question.versionId().toString(),
                                "title", "练习中发帖",
                                "body", "不应成功"))))
                .andExpect(status().isConflict());

        session.setStatus(PracticeSession.Status.COMPLETED);
        practiceSessions.save(session);

        mockMvc.perform(get("/api/v1/questions/{questionId}/discussions", question.questionId())
                        .with(appUser(authorId, usernameFor(authorId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void publishingANewVersionKeepsOldDiscussionsBoundToTheirVersion() throws Exception {
        P1TestSupport.SubjectChapter catalog = createSubjectAndChapter();
        P1TestSupport.QuestionRef question = createPublishedChoice(
                catalog.subjectId(), catalog.chapterId(), "Versioned discussion", "C", null);
        UUID authorId = registerUser();
        createDiscussion(
                authorId, question.questionId(), question.versionId(),
                "旧版本讨论", "讨论绑定发布时的版本。");

        QuestionDtos.AdminQuestionDetail cloned = authoringService.cloneVersion(
                ADMIN_ID, question.questionId());
        UUID newVersionId = cloned.versions().stream()
                .filter(version -> "DRAFT".equals(version.status()))
                .map(QuestionDtos.VersionView::versionId)
                .findFirst()
                .orElseThrow();
        publish(newVersionId);

        createDiscussion(
                authorId, question.questionId(), newVersionId,
                "新版本讨论", "新版本发布后可以绑定新版本。");

        mockMvc.perform(get("/api/v1/questions/{questionId}/discussions", question.questionId())
                        .with(appUser(authorId, usernameFor(authorId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[*].versionNo", containsInAnyOrder(1, 2)))
                .andExpect(jsonPath("$.items[*].versionCurrent", containsInAnyOrder(true, false)));

        Map<String, Object> stalePayload = new HashMap<>();
        stalePayload.put("questionVersionId", question.versionId().toString());
        stalePayload.put("title", "旧版本发帖");
        stalePayload.put("body", "只能绑定当前发布版本。");
        mockMvc.perform(post("/api/v1/questions/{questionId}/discussions", question.questionId())
                        .with(appUser(authorId, usernameFor(authorId)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stalePayload)))
                .andExpect(status().isConflict());
    }

    @Test
    void postedContentFiltersLinksAndSensitiveWords() throws Exception {
        P1TestSupport.SubjectChapter catalog = createSubjectAndChapter();
        P1TestSupport.QuestionRef question = createPublishedChoice(
                catalog.subjectId(), catalog.chapterId(), "Filtered discussion", "D", null);
        UUID authorId = registerUser();

        UUID postId = createDiscussion(
                authorId, question.questionId(), question.versionId(),
                "过滤测试",
                "请看 http://spam.example 这个广告链接。");

        MvcResult detail = mockMvc.perform(get("/api/v1/discussions/{postId}", postId)
                        .with(appUser(authorId, usernameFor(authorId))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = readJson(detail).get("body");
        String text = body.asText();
        org.junit.jupiter.api.Assertions.assertTrue(text.contains("[链接已屏蔽]"));
        org.junit.jupiter.api.Assertions.assertFalse(text.contains("http"));
        org.junit.jupiter.api.Assertions.assertFalse(text.contains("广告"));
    }
}
