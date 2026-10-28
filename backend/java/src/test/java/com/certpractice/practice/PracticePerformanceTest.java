package com.certpractice.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.certpractice.support.P1TestSupport;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Records P1 baselines without enforcing unconfirmed product thresholds. The
 * assertions are deliberately generous regression guards.
 */
class PracticePerformanceTest extends P1TestSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(PracticePerformanceTest.class);
    private static final int QUESTION_COUNT = 25;
    private static final int ROUNDS = 3;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void recordSessionAndSearchBaselines() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        for (int index = 0; index < QUESTION_COUNT; index++) {
            createPublishedChoice(catalog.subjectId(), catalog.chapterId(),
                    "baseline question " + index + " " + UUID.randomUUID(), "B", List.of());
        }
        UUID userId = registerUser();

        long sessionMax = 0;
        long sessionTotal = 0;
        for (int round = 0; round < ROUNDS; round++) {
            Map<String, Object> body = Map.of(
                    "mode", "NORMAL",
                    "filters", Map.of("subjectId", catalog.subjectId().toString()),
                    "requestedCount", 10);
            long started = System.nanoTime();
            mockMvc.perform(post("/api/v1/practice-sessions")
                            .with(appUser(userId, "user"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated());
            long elapsed = (System.nanoTime() - started) / 1_000_000L;
            sessionMax = Math.max(sessionMax, elapsed);
            sessionTotal += elapsed;
        }

        long searchMax = 0;
        long searchTotal = 0;
        for (int round = 0; round < ROUNDS; round++) {
            long started = System.nanoTime();
            MvcResult result = mockMvc.perform(get("/api/v1/questions/search")
                            .with(appUser(userId, "user"))
                            .param("subjectId", catalog.subjectId().toString())
                            .param("size", "100"))
                    .andExpect(status().isOk())
                    .andReturn();
            long elapsed = (System.nanoTime() - started) / 1_000_000L;
            searchMax = Math.max(searchMax, elapsed);
            searchTotal += elapsed;
            assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                    .path("total").asInt()).isGreaterThanOrEqualTo(QUESTION_COUNT);
        }

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        Map<String, Object> body = Map.of(
                "mode", "NORMAL",
                "filters", Map.of("subjectId", catalog.subjectId().toString()),
                "requestedCount", 10);
        mockMvc.perform(post("/api/v1/practice-sessions")
                        .with(appUser(userId, "user"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
        long queryCount = statistics.getPrepareStatementCount();
        statistics.setStatisticsEnabled(false);

        LOGGER.info("p1_baseline sessionAvgMs={} sessionMaxMs={} searchAvgMs={} searchMaxMs={} "
                        + "sessionCreateStatements={}",
                sessionTotal / ROUNDS, sessionMax, searchTotal / ROUNDS, searchMax, queryCount);

        assertThat(sessionMax).isLessThan(10_000L);
        assertThat(searchMax).isLessThan(5_000L);
        assertThat(queryCount).isLessThan(500L);
    }
}
