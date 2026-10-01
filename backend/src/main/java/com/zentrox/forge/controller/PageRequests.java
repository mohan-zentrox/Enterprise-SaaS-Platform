package com.zentrox.forge.controller;

import org.springframework.data.domain.PageRequest;

/**
 * Builds a {@link PageRequest} from untrusted query parameters.
 *
 * Clamping the page size is a denial-of-service guard, not a nicety: without it
 * {@code ?size=10000000} on a list endpoint asks the database and the JSON serializer to
 * materialise an unbounded result set. Java 17 has no {@code Math.clamp}, hence the explicit
 * min/max.
 */
final class PageRequests {

    private PageRequests() {
    }

    static PageRequest of(int page, int size, int maxSize) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), maxSize);
        return PageRequest.of(safePage, safeSize);
    }
}
