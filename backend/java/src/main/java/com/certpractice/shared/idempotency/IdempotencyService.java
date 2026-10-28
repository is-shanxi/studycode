package com.certpractice.shared.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import com.certpractice.identity.IdempotencyRecord;
import com.certpractice.identity.IdempotencyRecordRepository;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/**
 * Replay protection for session creation, submission and self-assessment.
 * The same key with a different request hash is rejected.
 */
@Service
public class IdempotencyService {

    private static final long RETENTION_HOURS = 24;

    private final IdempotencyRecordRepository records;
    private final ObjectMapper objectMapper;

    public IdempotencyService(
            final IdempotencyRecordRepository records,
            final ObjectMapper objectMapper) {
        this.records = records;
        this.objectMapper = objectMapper;
    }

    public Optional<IdempotencyRecord> find(
            final UUID userId,
            final String operation,
            final String key,
            final Object payload) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        Optional<IdempotencyRecord> existing = records
                .findByUserIdAndOperationAndIdempotencyKey(userId, operation, key);
        if (existing.isPresent()) {
            String hash = hash(payload);
            if (!existing.get().getRequestHash().equals(hash)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_MISMATCH,
                        "Idempotency key was reused with a different request");
            }
        }
        return existing;
    }

    public <T> Optional<T> replay(final IdempotencyRecord record, final Class<T> type) {
        try {
            return Optional.of(objectMapper.readValue(record.getResponseBody(), type));
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Stored idempotent response is unreadable", exception);
        }
    }

    public void store(
            final UUID userId,
            final String operation,
            final String key,
            final Object payload,
            final int responseStatus,
            final Object responseBody) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (records.findByUserIdAndOperationAndIdempotencyKey(userId, operation, key).isPresent()) {
            return;
        }
        IdempotencyRecord record = new IdempotencyRecord();
        record.setUserId(userId);
        record.setOperation(operation);
        record.setIdempotencyKey(key);
        record.setRequestHash(hash(payload));
        record.setResponseStatus(responseStatus);
        record.setResponseBody(toJson(responseBody));
        record.setExpiresAt(Instant.now().plusSeconds(RETENTION_HOURS * 3600));
        records.save(record);
    }

    public String hash(final Object payload) {
        String canonical = toJson(payload);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String toJson(final Object payload) {
        if (payload == null) {
            return "";
        }
        if (payload instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Unable to serialize request for idempotency", exception);
        }
    }
}
