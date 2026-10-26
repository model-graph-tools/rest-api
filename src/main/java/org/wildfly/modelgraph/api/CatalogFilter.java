package org.wildfly.modelgraph.api;

import java.util.Arrays;
import java.util.List;

public final class CatalogFilter {

    private static final int DEFAULT_LIMIT = 50;
    private static final int DEFAULT_OFFSET = 0;
    private static final String DEFAULT_SORT = "name";
    private static final String DEFAULT_ORDER = "asc";

    private final String q;
    private final List<String> types;
    private final List<String> stabilities;
    private final String deprecated;
    private final String scope;
    private final Boolean singleton;
    private final String declaresCap;
    private final Boolean definesAttrGroups;
    private final String sort;
    private final String order;
    private final int limit;
    private final int offset;

    private CatalogFilter(String q, List<String> types, List<String> stabilities,
            String deprecated, String scope, Boolean singleton, String declaresCap,
            Boolean definesAttrGroups, String sort, String order,
            int limit, int offset) {
        this.q = q;
        this.types = types;
        this.stabilities = stabilities;
        this.deprecated = deprecated;
        this.scope = scope;
        this.singleton = singleton;
        this.declaresCap = declaresCap;
        this.definesAttrGroups = definesAttrGroups;
        this.sort = sort;
        this.order = order;
        this.limit = limit;
        this.offset = offset;
    }

    public static CatalogFilter of(String q, String type, String stability, String deprecated,
            String scope, Boolean singleton, String declaresCap, Boolean definesAttrGroups,
            String sort, String order, Integer limit, Integer offset) {
        String effectiveQ = (q != null && !q.isBlank()) ? q.strip() : null;
        List<String> types = parseMultiValue(type);
        List<String> stabilities = parseMultiValue(stability);
        String effectiveDeprecated = parseDeprecated(deprecated);
        String effectiveScope = (scope != null && !scope.isBlank()) ? scope.strip() : null;
        String effectiveDeclaresCap = (declaresCap != null && !declaresCap.isBlank())
                ? declaresCap.strip().toLowerCase()
                : null;
        String effectiveSort = parseSort(sort, effectiveQ);
        String effectiveOrder = parseOrder(order);
        int effectiveLimit = (limit != null && limit > 0) ? limit : DEFAULT_LIMIT;
        int effectiveOffset = (offset != null && offset >= 0) ? offset : DEFAULT_OFFSET;
        return new CatalogFilter(effectiveQ, types, stabilities, effectiveDeprecated,
                effectiveScope, singleton, effectiveDeclaresCap, definesAttrGroups,
                effectiveSort, effectiveOrder, effectiveLimit, effectiveOffset);
    }

    public String q() {
        return q;
    }

    public List<String> types() {
        return types;
    }

    public List<String> stabilities() {
        return stabilities;
    }

    public String deprecated() {
        return deprecated;
    }

    public String scope() {
        return scope;
    }

    public Boolean singleton() {
        return singleton;
    }

    public String declaresCap() {
        return declaresCap;
    }

    public Boolean definesAttrGroups() {
        return definesAttrGroups;
    }

    public String sort() {
        return sort;
    }

    public String order() {
        return order;
    }

    public int limit() {
        return limit;
    }

    public int offset() {
        return offset;
    }

    public boolean hasSearchQuery() {
        return q != null;
    }

    public boolean hasTypeFilter() {
        return !types.isEmpty();
    }

    public boolean hasStabilityFilter() {
        return !stabilities.isEmpty();
    }

    public boolean hasDeprecatedFilter() {
        return deprecated != null;
    }

    public boolean hasScopeFilter() {
        return scope != null;
    }

    public boolean hasSingletonFilter() {
        return singleton != null;
    }

    public boolean hasDeclaresCapFilter() {
        return declaresCap != null;
    }

    public boolean declaresCapIsYes() {
        return "yes".equals(declaresCap);
    }

    public boolean declaresCapIsNo() {
        return "no".equals(declaresCap);
    }

    public String declaresCapContains() {
        if (declaresCap != null && declaresCap.startsWith("contains:")) {
            String text = declaresCap.substring("contains:".length()).strip();
            return text.isEmpty() ? null : text;
        }
        return null;
    }

    public boolean hasDefinesAttrGroupsFilter() {
        return definesAttrGroups != null;
    }

    public boolean includesType(CatalogType catalogType) {
        return !hasTypeFilter() || types.contains(catalogType.label());
    }

    static List<String> parseMultiValue(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::strip)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String parseDeprecated(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String lower = value.strip().toLowerCase();
        if ("yes".equals(lower) || "no".equals(lower)) {
            return lower;
        }
        return null;
    }

    private static String parseSort(String sort, String q) {
        if (sort != null && !sort.isBlank()) {
            String lower = sort.strip().toLowerCase();
            if (List.of("name", "type", "address", "stability").contains(lower)) {
                return lower;
            }
        }
        // When q is present and no explicit sort, default to score (relevance)
        if (q != null) {
            return "score";
        }
        return DEFAULT_SORT;
    }

    private static String parseOrder(String order) {
        if (order != null && !order.isBlank()) {
            String lower = order.strip().toLowerCase();
            if ("desc".equals(lower)) {
                return "desc";
            }
        }
        return DEFAULT_ORDER;
    }
}
