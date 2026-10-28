package com.certpractice.aigateway;

import java.util.List;
import java.util.UUID;

import com.certpractice.aigateway.dto.AiChatRequest;
import com.certpractice.aigateway.dto.AiChatResponse;
import com.certpractice.aigateway.dto.AiGeneratedCandidate;
import com.certpractice.aigateway.dto.AiVariantGenerationRequest;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.observability.TraceContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Restricted internal REST client for the Python AI service. The browser never
 * calls Python directly, and Python never reads business tables.
 */
@Service
public class AiServiceClient {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String CHAT_PATH = "/internal/v1/chat";
    private static final String GENERATE_PATH = "/internal/v1/generate-variants";

    private final RestClient restClient;
    private final AiProperties properties;

    public AiServiceClient(final RestClient.Builder restClientBuilder, final AiProperties properties) {
        if (properties.serviceToken() == null || properties.serviceToken().isBlank()) {
            throw new IllegalStateException("app.ai.service-token must be configured");
        }
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()));
        requestFactory.setReadTimeout(Math.toIntExact(properties.requestTimeout().toMillis()));
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public AiChatResponse chat(final AiChatRequest request, final String traceId) {
        try {
            return restClient.post()
                    .uri(CHAT_PATH)
                    .headers(headers -> configureHeaders(headers, traceId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiChatResponse.class);
        } catch (RestClientResponseException | ResourceAccessException exception) {
            throw new ApiException(ErrorCode.AI_UNAVAILABLE, "AI service is unavailable", exception);
        }
    }

    public List<AiGeneratedCandidate> generateVariants(
            final AiVariantGenerationRequest request,
            final String traceId) {
        try {
            List<AiGeneratedCandidate> candidates = restClient.post()
                    .uri(GENERATE_PATH)
                    .headers(headers -> configureHeaders(headers, traceId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<AiGeneratedCandidate>>() {
                    });
            return candidates == null ? List.of() : List.copyOf(candidates);
        } catch (RestClientResponseException | ResourceAccessException exception) {
            throw new ApiException(ErrorCode.AI_UNAVAILABLE, "AI service is unavailable", exception);
        }
    }

    private void configureHeaders(final HttpHeaders headers, final String traceId) {
        String effectiveTraceId = TraceContext.validOrNull(traceId);
        if (effectiveTraceId == null) {
            effectiveTraceId = UUID.randomUUID().toString();
        }
        headers.set(INTERNAL_TOKEN_HEADER, properties.serviceToken());
        headers.set(TraceContext.TRACE_ID_HEADER, effectiveTraceId);
        headers.set(TraceContext.REQUEST_ID_HEADER, UUID.randomUUID().toString());
    }
}
