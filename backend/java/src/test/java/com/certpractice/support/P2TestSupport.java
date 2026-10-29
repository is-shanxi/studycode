package com.certpractice.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.certpractice.practice.PracticeSession;
import com.certpractice.practice.PracticeSessionRepository;
import com.certpractice.practice.SessionItem;
import com.certpractice.practice.SessionItemRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Shared fixtures for P2 community, notification and moderation tests. Each
 * test creates its own catalog, question and users so tests remain independent.
 */
public abstract class P2TestSupport extends P1TestSupport {

    @Autowired
    protected PracticeSessionRepository practiceSessions;

    @Autowired
    protected SessionItemRepository sessionItems;

    protected String usernameFor(final UUID userId) {
        return "user-" + userId.toString().substring(0, 8);
    }

    protected UUID createDiscussion(
            final UUID userId,
            final UUID questionId,
            final UUID versionId,
            final String title,
            final String body) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("questionVersionId", versionId.toString());
        payload.put("title", title);
        payload.put("body", body);

        MvcResult result = mockMvc.perform(post("/api/v1/questions/{questionId}/discussions", questionId)
                        .with(appUser(userId, usernameFor(userId)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(result).get("postId").asText());
    }

    protected UUID createComment(
            final UUID userId,
            final UUID postId,
            final UUID parentCommentId,
            final UUID quotedCommentId,
            final String body) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("parentCommentId", parentCommentId == null ? null : parentCommentId.toString());
        payload.put("quotedCommentId", quotedCommentId == null ? null : quotedCommentId.toString());
        payload.put("body", body);

        MvcResult result = mockMvc.perform(post("/api/v1/discussions/{postId}/comments", postId)
                        .with(appUser(userId, usernameFor(userId)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(result).get("commentId").asText());
    }

    protected PracticeSession createInProgressSession(
            final UUID userId,
            final UUID questionId,
            final UUID versionId) {
        PracticeSession session = new PracticeSession();
        session.setUserId(userId);
        session.setMode(PracticeSession.Mode.NORMAL);
        session.setStatus(PracticeSession.Status.IN_PROGRESS);
        session.setFilterSnapshot("{}");
        session.setRuleSnapshot("{}");
        session.setRequestedCount(1);
        session.setActualCount(1);
        session.setOriginalCount(1);
        session.setAiVariantCount(0);
        practiceSessions.save(session);

        SessionItem item = new SessionItem();
        item.setSessionId(session.getId());
        item.setOrdinal(1);
        item.setQuestionId(questionId);
        item.setQuestionVersionId(versionId);
        item.setSourceType(SessionItem.SourceType.ORIGINAL);
        item.setSelectionBucket(SessionItem.SelectionBucket.UNSEEN);
        sessionItems.save(item);
        return session;
    }

    protected JsonNode readJson(final MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
