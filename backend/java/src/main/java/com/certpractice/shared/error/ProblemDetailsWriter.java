package com.certpractice.shared.error;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.certpractice.shared.observability.TraceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Writes Problem Details directly to servlet responses from security filters,
 * where @RestControllerAdvice is not involved.
 */
@Component
public class ProblemDetailsWriter {

    public static final MediaType PROBLEM_JSON = MediaType.valueOf("application/problem+json");

    private final ObjectMapper objectMapper;

    public ProblemDetailsWriter(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(
            final HttpServletResponse response,
            final ErrorCode errorCode,
            final String detail) throws IOException {
        write(response, errorCode, detail, null);
    }

    public void write(
            final HttpServletResponse response,
            final ErrorCode errorCode,
            final String detail,
            final List<ProblemDetails.FieldErrorDetail> errors) throws IOException {
        ProblemDetails problem = ProblemDetails.from(
                errorCode,
                detail,
                TraceContext.currentTraceId(),
                errors);
        write(response, problem);
    }

    public void write(final HttpServletResponse response, final ProblemDetails problem) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(problem.status());
        response.setContentType(PROBLEM_JSON.toString());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
