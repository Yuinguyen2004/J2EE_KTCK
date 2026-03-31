package com.billiard.shared.web;

import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * @desc Normalizes pagination and validates sort fields so each controller can
 * expose the same query contract without repeating guardrails.
 */
public final class PageRequestFactory {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private PageRequestFactory() {
    }

    public static Pageable create(
            Integer page,
            Integer size,
            String sortBy,
            String direction,
            String defaultSortBy,
            Map<String, String> sortFieldAllowlist
    ) {
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        int resolvedSize = size == null ? DEFAULT_SIZE : size;

        if (resolvedPage < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0");
        }

        if (resolvedSize < 1 || resolvedSize > MAX_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "size must be between 1 and " + MAX_SIZE
            );
        }

        String requestedSort = normalize(sortBy);
        String sortKey = requestedSort == null ? defaultSortBy : requestedSort;
        String sortProperty = sortFieldAllowlist.get(sortKey);
        if (sortProperty == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sortBy value");
        }

        Sort.Direction sortDirection = Sort.Direction.fromOptionalString(
                direction == null ? "DESC" : direction.toUpperCase(Locale.ROOT)
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "direction must be ASC or DESC"
        ));

        return PageRequest.of(resolvedPage, resolvedSize, Sort.by(sortDirection, sortProperty));
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
