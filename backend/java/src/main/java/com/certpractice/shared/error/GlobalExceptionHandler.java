package com.certpractice.shared.error;

import java.util.List;

import com.certpractice.shared.observability.TraceContext;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps expected failures to Problem Details without leaking SQL, stack traces,
 * secrets, answer material or other users' private data.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetails> handleApiException(final ApiException exception) {
        return problem(exception.getErrorCode(), exception.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetails> handleBeanValidation(final MethodArgumentNotValidException exception) {
        List<ProblemDetails.FieldErrorDetail> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ProblemDetails.FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        return problem(ErrorCode.VALIDATION_FAILED, "Request validation failed", errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetails> handleConstraintViolation(final ConstraintViolationException exception) {
        List<ProblemDetails.FieldErrorDetail> errors = exception.getConstraintViolations()
                .stream()
                .map(violation -> new ProblemDetails.FieldErrorDetail(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()))
                .toList();
        return problem(ErrorCode.VALIDATION_FAILED, "Request validation failed", errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetails> handleMethodValidation(final HandlerMethodValidationException exception) {
        return problem(ErrorCode.VALIDATION_FAILED, "Request validation failed", null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetails> handleUnreadableBody(final HttpMessageNotReadableException exception) {
        return problem(ErrorCode.INVALID_REQUEST, "Malformed request body", null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetails> handleMissingParameter(
            final MissingServletRequestParameterException exception) {
        return problem(ErrorCode.INVALID_REQUEST, "Missing request parameter: " + exception.getParameterName(), null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetails> handleTypeMismatch(final MethodArgumentTypeMismatchException exception) {
        return problem(ErrorCode.INVALID_REQUEST, "Invalid value for parameter: " + exception.getName(), null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetails> handleDataIntegrity(final DataIntegrityViolationException exception) {
        LOGGER.warn("data_integrity_violation traceId={}", TraceContext.currentTraceId());
        return problem(ErrorCode.STATE_CONFLICT, "The request conflicts with existing data", null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetails> handleAuthentication(final AuthenticationException exception) {
        return problem(ErrorCode.UNAUTHENTICATED, "Authentication failed", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetails> handleAccessDenied(final AccessDeniedException exception) {
        return problem(ErrorCode.FORBIDDEN, "Access is denied", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetails> handleNoResource(final NoResourceFoundException exception) {
        return problem(ErrorCode.NOT_FOUND, "Resource not found", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetails> handleUnexpected(final Exception exception) {
        // Log only the exception type to avoid leaking request data or SQL parameters.
        LOGGER.error(
                "unhandled_exception type={} traceId={}",
                exception.getClass().getName(),
                TraceContext.currentTraceId());
        return problem(ErrorCode.INTERNAL_ERROR, "Internal server error", null);
    }

    private ResponseEntity<ProblemDetails> problem(
            final ErrorCode errorCode,
            final String detail,
            final List<ProblemDetails.FieldErrorDetail> errors) {
        ProblemDetails problem = ProblemDetails.from(errorCode, detail, TraceContext.currentTraceId(), errors);
        return ResponseEntity.status(errorCode.status())
                .contentType(ProblemDetailsWriter.PROBLEM_JSON)
                .body(problem);
    }
}
