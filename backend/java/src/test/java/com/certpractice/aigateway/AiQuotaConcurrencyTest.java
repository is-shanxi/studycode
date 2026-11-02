package com.certpractice.aigateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.certpractice.support.P3TestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Server-side AI quota, turn, budget and idempotency boundaries (AC-11).
 */
@TestPropertySource(properties = {
        "app.ai.quota.daily-request-limit=10",
        "app.ai.quota.session-turn-limit=20",
        "app.ai.quota.team-daily-budget=100",
        "app.ai.quota.reserved-cost=0.001"})
class AiQuotaConcurrencyTest extends P3TestSupport {

    @Test
    void personalDailyQuotaIsNeverExceededUnderConcurrentRequests() throws Exception {
        UUID userId = registerUser();
        List<UUID> conversations = List.of(
                createGeneralConversation(userId),
                createGeneralConversation(userId),
                createGeneralConversation(userId));

        List<Integer> statuses = runConcurrently(25, index -> sendMessageRaw(
                userId,
                conversations.get(index % conversations.size()),
                "concurrent question " + index,
                "quota-key-" + index).getResponse().getStatus());

        assertThat(count(statuses, 200)).isEqualTo(10);
        assertThat(count(statuses, 429)).isEqualTo(15);
        Integer used = jdbcTemplate.queryForObject("""
                SELECT request_count FROM ai_usage_daily
                WHERE user_id = ? AND usage_date = CURRENT_DATE
                """, Integer.class, userId);
        assertThat(used).isEqualTo(10);

        JsonNode quota = getJson("/api/v1/ai/quota", userId);
        assertThat(quota.path("used").asInt()).isEqualTo(10);
        assertThat(quota.path("remaining").asInt()).isZero();
    }

    @Test
    void sessionTurnLimitStopsRunawayConversations() throws Exception {
        UUID userId = registerUser();
        updateAiConfig(quotaConfig(100, 20, 100.0, 0.001, 2000));
        UUID conversationId = createGeneralConversation(userId);

        int success = 0;
        int rateLimited = 0;
        for (int index = 0; index < 25; index++) {
            int status = sendMessageRaw(userId, conversationId,
                    "turn " + index, "turn-key-" + index).getResponse().getStatus();
            if (status == 200) {
                success++;
            } else if (status == 429) {
                rateLimited++;
            }
        }

        assertThat(success).isEqualTo(20);
        assertThat(rateLimited).isEqualTo(5);
        Integer userMessages = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM ai_message
                WHERE conversation_id = ? AND role = 'USER'
                """, Integer.class, conversationId);
        assertThat(userMessages).isEqualTo(20);
    }

    @Test
    void teamDailyBudgetIsNeverExceededUnderConcurrentRequests() throws Exception {
        UUID userId = registerUser();
        updateAiConfig(quotaConfig(100, 100, 0.005, 0.001, 2000));
        List<UUID> conversations = List.of(
                createGeneralConversation(userId),
                createGeneralConversation(userId),
                createGeneralConversation(userId));

        List<Integer> statuses = runConcurrently(20, index -> sendMessageRaw(
                userId,
                conversations.get(index % conversations.size()),
                "budget question " + index,
                "budget-key-" + index).getResponse().getStatus());

        assertThat(count(statuses, 200)).isEqualTo(5);
        BigDecimal used = jdbcTemplate.queryForObject("""
                SELECT cost_used FROM ai_budget_daily
                WHERE scope = 'TEAM' AND usage_date = CURRENT_DATE
                """, BigDecimal.class);
        assertThat(used).isNotNull();
        assertThat(used).isLessThanOrEqualTo(new BigDecimal("0.005"));
    }

    @Test
    void idempotentRetriesConsumeOneQuotaUnit() throws Exception {
        UUID userId = registerUser();
        UUID conversationId = createGeneralConversation(userId);

        MvcResult first = sendMessage(userId, conversationId, "同一个问题", "idem-key-1", 200);
        String requestId = readJson(first).path("requestId").asText();
        for (int index = 0; index < 4; index++) {
            JsonNode replay = readJson(
                    sendMessage(userId, conversationId, "同一个问题", "idem-key-1", 200));
            assertThat(replay.path("requestId").asText()).isEqualTo(requestId);
        }

        JsonNode quota = getJson("/api/v1/ai/quota", userId);
        assertThat(quota.path("used").asInt()).isEqualTo(1);
        assertThat(quota.path("remaining").asInt()).isEqualTo(9);

        sendMessage(userId, conversationId, "不同的问题", "idem-key-1", 409);
    }

    @Test
    void quotaEndpointReportsUsageLimitsAndTeamAvailability() throws Exception {
        UUID userId = registerUser();

        JsonNode initial = getJson("/api/v1/ai/quota", userId);
        assertThat(initial.path("dailyLimit").asInt()).isEqualTo(10);
        assertThat(initial.path("used").asInt()).isZero();
        assertThat(initial.path("remaining").asInt()).isEqualTo(10);
        assertThat(initial.path("sessionTurnLimit").asInt()).isEqualTo(20);
        assertThat(initial.path("officialAnswerContextEnabled").asBoolean()).isFalse();
        assertThat(initial.path("teamBudget").path("available").asBoolean()).isTrue();

        UUID conversationId = createGeneralConversation(userId);
        sendMessage(userId, conversationId, "一次请求", "quota-once", 200);

        JsonNode after = getJson("/api/v1/ai/quota", userId);
        assertThat(after.path("used").asInt()).isEqualTo(1);
        assertThat(after.path("remaining").asInt()).isEqualTo(9);
    }

    @Test
    void failedRequestsRefundPersonalQuotaButKeepTheReservedBudget() throws Exception {
        UUID userId = registerUser();
        updateAiConfig(quotaConfig(10, 20, 0.01, 0.001, 2000));
        UUID conversationId = createGeneralConversation(userId);
        AI_SERVER.respondChat(500, "failed", 0, 0, 0);

        sendMessage(userId, conversationId, "这次调用会失败", "failed-key", 503);

        JsonNode quota = getJson("/api/v1/ai/quota", userId);
        assertThat(quota.path("used").asInt()).isZero();
        assertThat(quota.path("remaining").asInt()).isEqualTo(10);

        BigDecimal used = jdbcTemplate.queryForObject("""
                SELECT cost_used FROM ai_budget_daily
                WHERE scope = 'TEAM' AND usage_date = CURRENT_DATE
                """, BigDecimal.class);
        assertThat(used).isNotNull();
        assertThat(used).isEqualByComparingTo("0.001");

        String status = jdbcTemplate.queryForObject("""
                SELECT status FROM ai_request WHERE idempotency_key = 'failed-key'
                """, String.class);
        assertThat(status).isEqualTo("REFUNDED");
    }

    @Test
    void rejectsInputLongerThanTheConfiguredMaximum() throws Exception {
        UUID userId = registerUser();
        updateAiConfig(quotaConfig(10, 20, 100.0, 0.001, 10));
        UUID conversationId = createGeneralConversation(userId);

        MvcResult result = sendMessage(userId, conversationId,
                "12345678901", "too-long-key", 400);
        assertThat(readJson(result).path("code").asText()).isEqualTo("VALIDATION_FAILED");
    }

    private Map<String, Object> quotaConfig(
            final int dailyLimit,
            final int sessionTurnLimit,
            final double teamDailyBudget,
            final double reservedCost,
            final int maxInputChars) {
        return Map.of(
                "dailyRequestLimit", dailyLimit,
                "sessionTurnLimit", sessionTurnLimit,
                "teamDailyBudget", teamDailyBudget,
                "reservedCost", reservedCost,
                "maxInputChars", maxInputChars,
                "sendOfficialAnswer", false);
    }

    private void updateAiConfig(final Map<String, Object> values) throws Exception {
        Map<String, Object> payload = Map.of("key", "ai.settings", "value", values);
        mockMvc.perform(put("/api/v1/admin/configs")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    private List<Integer> runConcurrently(final int count, final Task task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < count; index++) {
                int taskIndex = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent test start timed out");
                    }
                    return task.run(taskIndex);
                }));
            }
            if (!ready.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent test workers did not become ready");
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(60, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            executor.shutdownNow();
        }
    }

    private int count(final List<Integer> values, final int expected) {
        return (int) values.stream().filter(value -> value == expected).count();
    }

    @FunctionalInterface
    private interface Task {
        int run(int index) throws Exception;
    }
}
