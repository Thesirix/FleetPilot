package com.fleetpilot.common.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Stable JSON shape for paginated REST responses (Spring's {@code Page} serialization is not a public contract).
 *
 * @param content       items of the current page
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements total number of items matching the query
 * @param totalPages    total number of pages
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public PageResponse {
        content = List.copyOf(content);
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }

    public static <S, T> PageResponse<T> of(Page<S> page, Function<? super S, ? extends T> mapper) {
        return of(page.map(mapper::apply));
    }
}
