package com.certpractice.shared.web;

import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Validated zero-based page request shared by all list endpoints.
 */
public record PageQuery(int page, int size) {

    public static final int MAX_SIZE = 100;
    public static final int DEFAULT_SIZE = 20;

    public PageQuery {
        if (page < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "page must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "size must be between 1 and " + MAX_SIZE);
        }
    }

    public static PageQuery defaults() {
        return new PageQuery(0, DEFAULT_SIZE);
    }

    public Pageable toPageable() {
        return PageRequest.of(page, size);
    }

    public Pageable toPageable(final Sort sort) {
        return PageRequest.of(page, size, sort);
    }
}
