package com.zentrox.forge.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Stable pagination envelope for list endpoints.
 *
 * Deliberately not Spring Data's {@code Page}/{@code PageImpl}: Spring Boot 3.3 warns against
 * serializing those directly because their JSON shape is an implementation detail that has changed
 * between versions. This record is part of our published API contract instead (see docs/API.md).
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages,
                               boolean hasNext) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }
}
