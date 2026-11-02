package com.certpractice.aigateway;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.certpractice.identity.AdminAuditLog;
import com.certpractice.identity.AdminAuditService;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Versioned AI policy. Runtime overrides live in {@code system_config_version};
 * defaults come from configuration properties. Reads always take the latest
 * version, while existing sessions keep the snapshots they were created with.
 */
@Service
public class AiConfigService {

    public static final String AI_SETTINGS_KEY = "ai.settings";

    private final SystemConfigVersionRepository versions;
    private final AiQuotaProperties quotaProperties;
    private final AiContextProperties contextProperties;
    private final ObjectMapper objectMapper;
    private final AdminAuditService audit;
    private final TransactionTemplate transactionTemplate;
    private final Object updateLock = new Object();

    public AiConfigService(
            final SystemConfigVersionRepository versions,
            final AiQuotaProperties quotaProperties,
            final AiContextProperties contextProperties,
            final ObjectMapper objectMapper,
            final AdminAuditService audit,
            final PlatformTransactionManager transactionManager) {
        this.versions = versions;
        this.quotaProperties = quotaProperties;
        this.contextProperties = contextProperties;
        this.objectMapper = objectMapper;
        this.audit = audit;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public AiSettings current() {
        Optional<SystemConfigVersion> latest =
                versions.findFirstByConfigKeyOrderByVersionNoDesc(AI_SETTINGS_KEY);
        if (latest.isEmpty()) {
            return defaults();
        }
        return parse(latest.get().getConfigValueJson());
    }

    public AiAdminDtos.ConfigView latest(final String key) {
        requireSupportedKey(key);
        Optional<SystemConfigVersion> latest =
                versions.findFirstByConfigKeyOrderByVersionNoDesc(AI_SETTINGS_KEY);
        if (latest.isEmpty()) {
            return new AiAdminDtos.ConfigView(
                    AI_SETTINGS_KEY, 0, null, null, toJsonNode(defaults()));
        }
        SystemConfigVersion entity = latest.get();
        return new AiAdminDtos.ConfigView(
                entity.getConfigKey(),
                entity.getVersionNo(),
                entity.getEffectiveFrom(),
                entity.getCreatedBy(),
                readTree(entity.getConfigValueJson()));
    }

    public AiAdminDtos.ConfigView update(
            final UUID adminId,
            final String key,
            final JsonNode value) {
        requireSupportedKey(key);
        AiSettings settings = parse(value);
        synchronized (updateLock) {
            return transactionTemplate.execute(status -> {
                int nextVersion = versions
                        .findFirstByConfigKeyOrderByVersionNoDesc(AI_SETTINGS_KEY)
                        .map(existing -> existing.getVersionNo() + 1)
                        .orElse(1);
                SystemConfigVersion entity = new SystemConfigVersion();
                entity.setConfigKey(AI_SETTINGS_KEY);
                entity.setConfigValueJson(writeJson(settings));
                entity.setVersionNo(nextVersion);
                entity.setCreatedBy(adminId);
                versions.save(entity);
                audit.record(adminId, "AI_CONFIG_UPDATE", "SYSTEM_CONFIG", entity.getId(),
                        AdminAuditLog.Outcome.SUCCESS,
                        Map.of("key", AI_SETTINGS_KEY, "versionNo", nextVersion));
                return new AiAdminDtos.ConfigView(
                        AI_SETTINGS_KEY,
                        nextVersion,
                        entity.getEffectiveFrom(),
                        adminId,
                        toJsonNode(settings));
            });
        }
    }

    public AiSettings defaults() {
        return new AiSettings(
                quotaProperties.getDailyRequestLimit(),
                quotaProperties.getSessionTurnLimit(),
                quotaProperties.getMaxInputChars(),
                quotaProperties.getTeamDailyBudget(),
                quotaProperties.getReservedCost(),
                contextProperties.isSendOfficialAnswer());
    }

    AiSettings parse(final String json) {
        try {
            return parse(objectMapper.readTree(json));
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Stored AI configuration is unreadable", exception);
        }
    }

    AiSettings parse(final JsonNode value) {
        if (value == null || !value.isObject()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "AI configuration must be a JSON object");
        }
        AiSettings defaults = defaults();
        int dailyLimit = intValue(value, "dailyRequestLimit",
                defaults.dailyRequestLimit(), 1, 1000);
        int turnLimit = intValue(value, "sessionTurnLimit",
                defaults.sessionTurnLimit(), 1, 200);
        int maxInputChars = intValue(value, "maxInputChars",
                defaults.maxInputChars(), 1, 20000);
        BigDecimal budget = decimalValue(value, "teamDailyBudget",
                defaults.teamDailyBudget(), false);
        BigDecimal reservedCost = decimalValue(value, "reservedCost",
                defaults.reservedCost(), true);
        boolean sendOfficialAnswer = value.has("sendOfficialAnswer")
                ? value.get("sendOfficialAnswer").asBoolean()
                : defaults.sendOfficialAnswer();
        return new AiSettings(dailyLimit, turnLimit, maxInputChars,
                budget, reservedCost, sendOfficialAnswer);
    }

    private int intValue(
            final JsonNode value,
            final String field,
            final int fallback,
            final int minimum,
            final int maximum) {
        if (!value.has(field)) {
            return fallback;
        }
        JsonNode node = value.get(field);
        if (!node.isIntegralNumber()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    field + " must be an integer");
        }
        int parsed = node.asInt();
        if (parsed < minimum || parsed > maximum) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    field + " must be between " + minimum + " and " + maximum);
        }
        return parsed;
    }

    private BigDecimal decimalValue(
            final JsonNode value,
            final String field,
            final BigDecimal fallback,
            final boolean strictlyPositive) {
        if (!value.has(field)) {
            return fallback;
        }
        JsonNode node = value.get(field);
        if (!node.isNumber()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    field + " must be a number");
        }
        BigDecimal parsed = node.decimalValue();
        boolean invalid = strictlyPositive ? parsed.signum() <= 0 : parsed.signum() < 0;
        if (invalid) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    field + (strictlyPositive ? " must be positive" : " must not be negative"));
        }
        return parsed;
    }

    private void requireSupportedKey(final String key) {
        if (!AI_SETTINGS_KEY.equals(key)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Unknown configuration key");
        }
    }

    private String writeJson(final AiSettings settings) {
        try {
            return objectMapper.writeValueAsString(toMap(settings));
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Unable to serialize AI configuration", exception);
        }
    }

    private JsonNode toJsonNode(final AiSettings settings) {
        return objectMapper.valueToTree(toMap(settings));
    }

    private Map<String, Object> toMap(final AiSettings settings) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("dailyRequestLimit", settings.dailyRequestLimit());
        values.put("sessionTurnLimit", settings.sessionTurnLimit());
        values.put("maxInputChars", settings.maxInputChars());
        values.put("teamDailyBudget", settings.teamDailyBudget());
        values.put("reservedCost", settings.reservedCost());
        values.put("sendOfficialAnswer", settings.sendOfficialAnswer());
        return values;
    }

    private JsonNode readTree(final String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Stored AI configuration is unreadable", exception);
        }
    }

    /**
     * Effective AI policy for one request or quota check.
     */
    public record AiSettings(
            int dailyRequestLimit,
            int sessionTurnLimit,
            int maxInputChars,
            BigDecimal teamDailyBudget,
            BigDecimal reservedCost,
            boolean sendOfficialAnswer) {
    }
}
