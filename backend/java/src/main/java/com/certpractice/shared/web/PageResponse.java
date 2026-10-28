package com.certpractice.shared.web;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Stable public pagination envelope defined by the HTTP contract.
 */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

    public static <T> PageResponse<T> from(final Page<T> source) {
        return new PageResponse<>(
                List.copyOf(source.getContent()),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements());
    }
}
