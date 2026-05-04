package com.cresensolutions.leaveservice.common;

import java.util.Locale;

public final class StringUtils {

    private StringUtils() {}

    public static String safe(String value) {
        return value != null ? value : "";
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    public static String normalizeRole(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public static String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String normalizeOptional(String value) {
        return value == null ? null : value.trim();
    }

    public static String requireNonBlank(String value, String message) {
        String trimmed = normalizeOptional(value);
        if (trimmed == null || trimmed.isBlank()) throw new IllegalArgumentException(message);
        return trimmed;
    }

    public static String extractString(java.util.Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof String s ? s : null;
    }
}
