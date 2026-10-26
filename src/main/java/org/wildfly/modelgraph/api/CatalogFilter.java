package org.wildfly.modelgraph.api;

import java.util.Arrays;
import java.util.List;

public final class CatalogFilter {

    private static final int DEFAULT_LIMIT = 50;
    private static final int DEFAULT_OFFSET = 0;

    private final List<String> types;
    private final List<String> stabilities;
    private final int limit;
    private final int offset;

    private CatalogFilter(List<String> types, List<String> stabilities, int limit, int offset) {
        this.types = types;
        this.stabilities = stabilities;
        this.limit = limit;
        this.offset = offset;
    }

    public static CatalogFilter of(String type, String stability, Integer limit, Integer offset) {
        List<String> types = parseMultiValue(type);
        List<String> stabilities = parseMultiValue(stability);
        int effectiveLimit = (limit != null && limit > 0) ? limit : DEFAULT_LIMIT;
        int effectiveOffset = (offset != null && offset >= 0) ? offset : DEFAULT_OFFSET;
        return new CatalogFilter(types, stabilities, effectiveLimit, effectiveOffset);
    }

    public List<String> types() {
        return types;
    }

    public List<String> stabilities() {
        return stabilities;
    }

    public int limit() {
        return limit;
    }

    public int offset() {
        return offset;
    }

    public boolean hasTypeFilter() {
        return !types.isEmpty();
    }

    public boolean hasStabilityFilter() {
        return !stabilities.isEmpty();
    }

    public boolean includesType(CatalogType catalogType) {
        return !hasTypeFilter() || types.contains(catalogType.label());
    }

    private static List<String> parseMultiValue(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::strip)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
