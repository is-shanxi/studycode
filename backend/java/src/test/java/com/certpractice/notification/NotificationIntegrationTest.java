package com.certpractice.notification;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import com.certpractice.support.P1TestSupport;
import com.certpractice.support.P2TestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class NotificationIntegrationTest extends P2TestSupport {

    @Test
    void commentRepliesCreatePersistentUnreadNotifications() throws Exception {
        P1TestSupport.SubjectChapter catalog = createSubjectAndChapter();
        P1TestSupport.QuestionRef question = createPublishedChoice(
                catalog.subjectId(), catalog.chapterId(), "Notification question", "B", null);
        UUID authorId = registerUser();
        UUID replierId = registerUser();
        UUID outsiderId = registerUser();

        UUID postId = createDiscussion(
                authorId, question.questionId(), question.versionId(),
                "通知测试帖子", "有人回复时应收到站内通知。");
        createComment(authorId, postId, null, null, "作者自己的评论不应通知自己。");

        mockMvc.perform(get("/api/v1/me/notifications")
                        .with(appUser(authorId, usernameFor(authorId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));

        createComment(replierId, postId, null, null, "这是一条回复。");

        MvcResult listed = mockMvc.perform(get("/api/v1/me/notifications")
                        .with(appUser(authorId, usernameFor(authorId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("COMMENT_REPLY"))
                .andExpect(jsonPath("$.items[0].readAt").doesNotExist())
                .andReturn();
        UUID notificationId = UUID.fromString(
                readJson(listed).get("items").get(0).get("notificationId").asText());

        mockMvc.perform(get("/api/v1/me/notifications")
                        .queryParam("unreadOnly", "true")
                        .with(appUser(authorId, usernameFor(authorId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(put("/api/v1/me/notifications/{notificationId}/read", notificationId)
                        .with(appUser(authorId, usernameFor(authorId)))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/me/notifications")
                        .with(appUser(authorId, usernameFor(authorId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].readAt").exists());

        mockMvc.perform(get("/api/v1/me/notifications")
                        .queryParam("unreadOnly", "true")
                        .with(appUser(authorId, usernameFor(authorId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));

        mockMvc.perform(put("/api/v1/me/notifications/{notificationId}/read", notificationId)
                        .with(appUser(outsiderId, usernameFor(outsiderId)))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        createComment(replierId, postId, null, null, "再回复一次以测试全部已读。");
        mockMvc.perform(put("/api/v1/me/notifications/read-all")
                        .with(appUser(authorId, usernameFor(authorId)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1));

        mockMvc.perform(get("/api/v1/me/notifications")
                        .queryParam("unreadOnly", "true")
                        .with(appUser(authorId, usernameFor(authorId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void administratorsCanBroadcastAnnouncements() throws Exception {
        UUID firstUserId = registerUser();
        UUID secondUserId = registerUser();

        MvcResult broadcast = mockMvc.perform(post("/api/v1/admin/notifications/announcements")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "系统维护通知",
                                "body", "本周日 22:00 进行维护。"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recipientCount").isNumber())
                .andReturn();
        int recipientCount = readJson(broadcast).get("recipientCount").asInt();
        org.junit.jupiter.api.Assertions.assertTrue(recipientCount >= 2);

        mockMvc.perform(get("/api/v1/me/notifications")
                        .with(appUser(firstUserId, usernameFor(firstUserId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.type == 'ANNOUNCEMENT')].title")
                        .value(org.hamcrest.Matchers.hasItem("系统维护通知")));

        mockMvc.perform(post("/api/v1/admin/notifications/announcements")
                        .with(appUser(secondUserId, usernameFor(secondUserId)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "越权公告",
                                "body", "普通用户不能发送公告。"))))
                .andExpect(status().isForbidden());
    }
}
