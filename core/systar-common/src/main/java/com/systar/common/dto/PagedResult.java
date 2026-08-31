package com.systar.common.dto;

import java.util.List;

/**
 * Framework-independent paged result container.
 * Serializes to the same JSON shape as MyBatis-Plus {@code Page}
 * ({@code records}, {@code total}, {@code current}, {@code size}).
 */
public record PagedResult<T>(
        List<T> records,
        long total,
        long current,
        long size
) {
}
