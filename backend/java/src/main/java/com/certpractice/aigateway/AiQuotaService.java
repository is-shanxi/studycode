package com.certpractice.aigateway;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomic personal quota and team budget accounting. All reservations use
 * conditional SQL updates inside the surrounding JPA transaction, so concurrent
 * requests can never exceed the limits. A transaction-scoped advisory lock gives
 * the daily ledgers a single lock order.
 */
@Service
public class AiQuotaService {

    private final EntityManager entityManager;
    private final AiRequestRepository requests;
    private final AiConfigService configService;
    private final Clock clock;

    public AiQuotaService(
            final EntityManager entityManager,
            final AiRequestRepository requests,
            final AiConfigService configService,
            final Clock clock) {
        this.entityManager = entityManager;
        this.requests = requests;
        this.configService = configService;
        this.clock = clock;
    }

    public AiDtos.QuotaView quota(final UUID userId, final UUID conversationId) {
        AiConfigService.AiSettings settings = configService.current();
        LocalDate date = today();
        int used = countOrZero("""
                SELECT request_count FROM ai_usage_daily
                WHERE user_id = :userId AND usage_date = :date
                """, userId, date);
        BigDecimal budgetUsed = decimalOrZero("""
                SELECT cost_used FROM ai_budget_daily
                WHERE scope = 'TEAM' AND usage_date = :date
                """, date);
        BigDecimal budgetLimit = settings.teamDailyBudget();
        int turnsUsed = conversationId == null ? 0 : turnsUsed(conversationId);
        return new AiDtos.QuotaView(
                date,
                settings.dailyRequestLimit(),
                used,
                Math.max(0, settings.dailyRequestLimit() - used),
                settings.sessionTurnLimit(),
                turnsUsed,
                settings.maxInputChars(),
                settings.sendOfficialAnswer(),
                new AiDtos.TeamBudgetView(
                        budgetLimit,
                        budgetUsed,
                        budgetUsed.compareTo(budgetLimit) < 0));
    }

    public int turnsUsed(final UUID conversationId) {
        Number turns = (Number) entityManager.createNativeQuery("""
                        SELECT count(*) FROM ai_message
                        WHERE conversation_id = :conversationId AND role = 'USER'
                        """)
                .setParameter("conversationId", conversationId)
                .getSingleResult();
        return turns == null ? 0 : turns.intValue();
    }

    @Transactional
    public Reservation reserve(
            final UUID userId,
            final UUID conversationId,
            final String idempotencyKey,
            final AiConfigService.AiSettings settings) {
        LocalDate date = today();
        // One transaction-scoped lock per day. It is a database lock, not an
        // in-memory counter, and it prevents concurrent ledger upserts from
        // deadlocking while keeping the two conditional updates atomic.
        lockLedgers(date);

        entityManager.createNativeQuery("""
                        INSERT INTO ai_usage_daily
                            (user_id, usage_date, request_count, input_tokens, output_tokens, actual_cost)
                        VALUES (:userId, :date, 0, 0, 0, 0)
                        ON CONFLICT (user_id, usage_date) DO NOTHING
                        """)
                .setParameter("userId", userId)
                .setParameter("date", date)
                .executeUpdate();
        int usageUpdated = entityManager.createNativeQuery("""
                        UPDATE ai_usage_daily SET request_count = request_count + 1
                        WHERE user_id = :userId AND usage_date = :date AND request_count < :limit
                        """)
                .setParameter("userId", userId)
                .setParameter("date", date)
                .setParameter("limit", settings.dailyRequestLimit())
                .executeUpdate();
        if (usageUpdated == 0) {
            throw new ApiException(ErrorCode.AI_QUOTA_EXCEEDED,
                    "Daily AI request quota has been exhausted");
        }

        entityManager.createNativeQuery("""
                        INSERT INTO ai_budget_daily (scope, usage_date, cost_limit, cost_used)
                        VALUES ('TEAM', :date, :limit, 0)
                        ON CONFLICT (scope, usage_date) DO UPDATE
                            SET cost_limit = EXCLUDED.cost_limit
                        """)
                .setParameter("date", date)
                .setParameter("limit", settings.teamDailyBudget())
                .executeUpdate();
        int budgetUpdated = entityManager.createNativeQuery("""
                        UPDATE ai_budget_daily SET cost_used = cost_used + :cost
                        WHERE scope = 'TEAM' AND usage_date = :date
                          AND cost_used + :cost <= cost_limit
                        """)
                .setParameter("cost", settings.reservedCost())
                .setParameter("date", date)
                .executeUpdate();
        if (budgetUpdated == 0) {
            throw new ApiException(ErrorCode.AI_QUOTA_EXCEEDED,
                    "Team daily AI budget has been exhausted");
        }

        AiRequest request = new AiRequest();
        request.setUserId(userId);
        request.setConversationId(conversationId);
        request.setIdempotencyKey(idempotencyKey == null || idempotencyKey.isBlank()
                ? "auto-" + UUID.randomUUID()
                : idempotencyKey);
        request.setQuotaDate(date);
        request.setStatus(AiRequest.Status.RESERVED);
        request.setReservedCost(settings.reservedCost());
        request.setActualCost(BigDecimal.ZERO);
        requests.saveAndFlush(request);
        return new Reservation(request.getId(), settings);
    }

    @Transactional
    public void settleSuccess(
            final UUID requestId,
            final int inputTokens,
            final int outputTokens,
            final String externalRequestId) {
        AiRequest request = requests.findById(requestId)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
                        "AI request reservation is missing"));
        if (request.getStatus() != AiRequest.Status.RESERVED) {
            return;
        }
        lockLedgers(request.getQuotaDate());
        request.setStatus(AiRequest.Status.SUCCEEDED);
        request.setActualCost(request.getReservedCost());
        request.setExternalRequestId(externalRequestId);
        request.setFinishedAt(clock.instant());
        requests.save(request);

        entityManager.createNativeQuery("""
                        UPDATE ai_usage_daily
                        SET input_tokens = input_tokens + :inputTokens,
                            output_tokens = output_tokens + :outputTokens,
                            actual_cost = actual_cost + :cost
                        WHERE user_id = :userId AND usage_date = :date
                        """)
                .setParameter("inputTokens", inputTokens)
                .setParameter("outputTokens", outputTokens)
                .setParameter("cost", request.getReservedCost())
                .setParameter("userId", request.getUserId())
                .setParameter("date", request.getQuotaDate())
                .executeUpdate();
    }

    @Transactional
    public void refund(final UUID requestId, final String errorCode) {
        AiRequest request = requests.findById(requestId).orElse(null);
        if (request == null || request.getStatus() != AiRequest.Status.RESERVED) {
            return;
        }
        lockLedgers(request.getQuotaDate());
        request.setStatus(AiRequest.Status.REFUNDED);
        request.setErrorCode(errorCode);
        // Personal counts are refunded, but the reserved team cost stays in the
        // daily ledger: a failed provider call may still have been billed.
        request.setActualCost(request.getReservedCost());
        request.setFinishedAt(clock.instant());
        requests.save(request);

        entityManager.createNativeQuery("""
                        UPDATE ai_usage_daily SET request_count = request_count - 1
                        WHERE user_id = :userId AND usage_date = :date AND request_count > 0
                        """)
                .setParameter("userId", request.getUserId())
                .setParameter("date", request.getQuotaDate())
                .executeUpdate();
    }

    private void lockLedgers(final LocalDate date) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:key))")
                .setParameter("key", "ai-quota-" + date)
                .getSingleResult();
    }

    private LocalDate today() {
        return clock.instant().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private int countOrZero(
            final String sql,
            final UUID userId,
            final LocalDate date) {
        List<?> values = entityManager.createNativeQuery(sql)
                .setParameter("userId", userId)
                .setParameter("date", date)
                .getResultList();
        if (values.isEmpty()) {
            return 0;
        }
        return ((Number) values.get(0)).intValue();
    }

    private BigDecimal decimalOrZero(final String sql, final LocalDate date) {
        List<?> values = entityManager.createNativeQuery(sql)
                .setParameter("date", date)
                .getResultList();
        if (values.isEmpty() || values.get(0) == null) {
            return BigDecimal.ZERO;
        }
        return (BigDecimal) values.get(0);
    }

    public record Reservation(UUID requestId, AiConfigService.AiSettings settings) {
    }
}
