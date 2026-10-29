package com.certpractice.admin;

import java.math.BigDecimal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Small operational dashboard backed by direct PostgreSQL aggregates. Objective
 * grading, automatic fill matching and self-assessment are reported separately
 * and never merged into a single "accuracy" number.
 */
@Service
public class DashboardService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final JdbcTemplate jdbcTemplate;

    public DashboardService(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public AdminDtos.DashboardView dashboard() {
        long activeUsers = count("SELECT count(*) FROM app_user WHERE status = 'ACTIVE'");
        long activeUsersLast7Days = count("""
                SELECT count(DISTINCT user_id)
                FROM practice_session
                WHERE started_at >= now() - interval '7 days'
                """);
        long totalSessions = count("SELECT count(*) FROM practice_session");
        long completedSessions = count(
                "SELECT count(*) FROM practice_session WHERE status = 'COMPLETED'");

        long objectiveGraded = count("""
                SELECT count(*)
                FROM session_item item
                JOIN question question ON question.id = item.question_id
                WHERE question.type = 'SINGLE_CHOICE'
                  AND item.final_result IS NOT NULL
                """);
        long objectiveFailed = count("""
                SELECT count(*)
                FROM session_item item
                JOIN question question ON question.id = item.question_id
                WHERE question.type = 'SINGLE_CHOICE'
                  AND item.final_result = 'FAIL'
                """);

        long fillAnswers = count("""
                SELECT count(*) FROM user_answer
                WHERE auto_match_result IN ('MATCH', 'MISMATCH')
                """);
        long autoMatched = count("""
                SELECT count(*) FROM user_answer WHERE auto_match_result = 'MATCH'
                """);

        long selfAssessed = count("SELECT count(*) FROM self_assessment");
        long selfPassed = count("SELECT count(*) FROM self_assessment WHERE result = 'PASS'");
        long selfFailed = count("SELECT count(*) FROM self_assessment WHERE result = 'FAIL'");

        long pendingReview = count(
                "SELECT count(*) FROM question_version WHERE status = 'PENDING_REVIEW'");
        long openReports = count("""
                SELECT
                    (SELECT count(*) FROM content_report WHERE status IN ('OPEN', 'PROCESSING'))
                    + (SELECT count(*) FROM correction_report WHERE status IN ('OPEN', 'PROCESSING'))
                """);
        long aiRequests = count(
                "SELECT coalesce(sum(request_count), 0) FROM ai_usage_daily");
        BigDecimal aiCost = jdbcTemplate.queryForObject(
                "SELECT coalesce(sum(actual_cost), 0) FROM ai_usage_daily", BigDecimal.class);

        return new AdminDtos.DashboardView(
                activeUsers,
                activeUsersLast7Days,
                totalSessions,
                completedSessions,
                rate(completedSessions, totalSessions),
                objectiveGraded,
                objectiveFailed,
                rate(objectiveFailed, objectiveGraded),
                fillAnswers,
                autoMatched,
                rate(autoMatched, fillAnswers),
                selfAssessed,
                selfPassed,
                selfFailed,
                rate(selfPassed, selfAssessed),
                pendingReview,
                openReports,
                aiRequests,
                aiCost == null ? ZERO : aiCost);
    }

    private long count(final String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private double rate(final long numerator, final long denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        double value = (double) numerator / denominator;
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
