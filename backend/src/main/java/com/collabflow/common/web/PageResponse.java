package com.collabflow.common.web;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * The pagination envelope returned by every list endpoint. Wrapping Spring Data's {@link Page}
 * instead of returning it directly keeps the API's response shape independent of the
 * persistence framework - {@code Page}'s own JSON serialization includes internal fields
 * (like a full {@code Pageable} object) that are implementation detail, not API contract.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
