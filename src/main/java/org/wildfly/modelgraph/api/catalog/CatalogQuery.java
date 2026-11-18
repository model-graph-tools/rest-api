package org.wildfly.modelgraph.api.catalog;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parsed and validated query parameters for the catalog endpoint. */
public record CatalogQuery(
        String q,
        List<String> types,
        List<String> stabilities,
        Boolean deprecated,
        String scope,
        Boolean singleton,
        String declaresCap,
        Boolean definesAttrGroups,
        List<String> dataTypes,
        List<String> accessTypes,
        List<String> storageValues,
        List<String> restartValues,
        Boolean required,
        Boolean nillable,
        Boolean expressions,
        List<String> units,
        Boolean hasAllowed,
        Boolean sensitive,
        Boolean hasRequires,
        Boolean hasAlternatives,
        String refsCap,
        Boolean readOnly,
        Boolean runtimeOnly,
        Boolean global,
        Boolean hasParams,
        Boolean hasReturnValue,
        String sort,
        String order,
        int limit,
        int offset) {

    // ------------------------------------------------------ factory

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;
    private static final int DEFAULT_OFFSET = 0;
    private static final String DEFAULT_SORT = "name";
    private static final String DEFAULT_ORDER = "asc";
    private static final Set<String> VALID_TYPES = Set.of("resource", "attribute", "operation", "capability");

    /** Factory that parses raw HTTP query parameters into a validated, normalized query — enforcing allowlists, capping limits, and resolving defaults. */
    public static CatalogQuery of(String q, String type, String stability, Boolean deprecated,
            String scope,
            Boolean singleton, String declaresCap, Boolean definesAttrGroups,
            String dataType, String accessType, String storage, String restart,
            Boolean required, Boolean nillable, Boolean expressions,
            String unit, Boolean hasAllowed,
            Boolean sensitive, Boolean hasRequires, Boolean hasAlternatives, String refsCap,
            Boolean readOnly, Boolean runtimeOnly, Boolean global,
            Boolean hasParams, Boolean hasReturnValue,
            String sort, String order, Integer limit, Integer offset) {
        String effectiveQ = (q != null && !q.isBlank()) ? q.strip() : null;
        List<String> types = parseMultiValue(type).stream()
                .filter(VALID_TYPES::contains)
                .toList();
        List<String> stabilities = parseMultiValue(stability);
        String effectiveScope = (scope != null && !scope.isBlank()) ? scope.strip() : null;
        String effectiveDeclaresCap = (declaresCap != null && !declaresCap.isBlank())
                ? declaresCap.strip().toLowerCase()
                : null;
        List<String> dataTypes = parseMultiValue(dataType);
        List<String> accessTypes = parseMultiValue(accessType);
        List<String> storageValues = parseMultiValue(storage);
        List<String> restartValues = parseMultiValue(restart);
        List<String> units = parseMultiValue(unit);
        String effectiveRefsCap = (refsCap != null && !refsCap.isBlank())
                ? refsCap.strip().toLowerCase()
                : null;
        String effectiveSort = parseSort(sort, types);
        String effectiveOrder = parseOrder(order);
        int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;
        int effectiveOffset = (offset != null && offset >= 0) ? offset : DEFAULT_OFFSET;
        return new CatalogQuery(effectiveQ, types, stabilities, deprecated, effectiveScope,
                singleton, effectiveDeclaresCap, definesAttrGroups,
                dataTypes, accessTypes, storageValues, restartValues, required, nillable,
                expressions, units, hasAllowed,
                sensitive, hasRequires, hasAlternatives, effectiveRefsCap,
                readOnly, runtimeOnly, global, hasParams, hasReturnValue,
                effectiveSort, effectiveOrder, effectiveLimit, effectiveOffset);
    }

    // ------------------------------------------------------ api

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

    public boolean declaresCapIsTrue() {
        return "true".equals(declaresCap);
    }

    public boolean declaresCapIsFalse() {
        return "false".equals(declaresCap);
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

    public boolean hasDataTypeFilter() {
        return !dataTypes.isEmpty();
    }

    public boolean hasAccessTypeFilter() {
        return !accessTypes.isEmpty();
    }

    public boolean hasStorageFilter() {
        return !storageValues.isEmpty();
    }

    public boolean hasRestartFilter() {
        return !restartValues.isEmpty();
    }

    public boolean hasRequiredFilter() {
        return required != null;
    }

    public boolean hasNillableFilter() {
        return nillable != null;
    }

    public boolean hasExpressionsFilter() {
        return expressions != null;
    }

    public boolean hasUnitFilter() {
        return !units.isEmpty();
    }

    public boolean hasHasAllowedFilter() {
        return hasAllowed != null;
    }

    public boolean hasSensitiveFilter() {
        return sensitive != null;
    }

    public boolean hasHasRequiresFilter() {
        return hasRequires != null;
    }

    public boolean hasHasAlternativesFilter() {
        return hasAlternatives != null;
    }

    public boolean hasRefsCapFilter() {
        return refsCap != null;
    }

    public boolean refsCapIsTrue() {
        return "true".equals(refsCap);
    }

    public boolean refsCapIsFalse() {
        return "false".equals(refsCap);
    }

    public String refsCapContains() {
        if (refsCap != null && refsCap.startsWith("contains:")) {
            String text = refsCap.substring("contains:".length()).strip();
            return text.isEmpty() ? null : text;
        }
        return null;
    }

    public boolean hasAnyAttributeFilter() {
        return hasDataTypeFilter() || hasAccessTypeFilter() || hasStorageFilter()
                || hasRestartFilter() || hasRequiredFilter() || hasNillableFilter()
                || hasExpressionsFilter() || hasUnitFilter() || hasHasAllowedFilter()
                || hasSensitiveFilter() || hasHasRequiresFilter() || hasHasAlternativesFilter()
                || hasRefsCapFilter();
    }

    public boolean hasAnyOperationFilter() {
        return hasReadOnlyFilter() || hasRuntimeOnlyFilter() || hasGlobalFilter()
                || hasParamsFilter() || hasReturnValueFilter();
    }

    public boolean hasAnyResourceFilter() {
        return hasSingletonFilter() || hasDeclaresCapFilter() || hasDefinesAttrGroupsFilter();
    }

    public boolean hasReadOnlyFilter() {
        return readOnly != null;
    }

    public boolean hasRuntimeOnlyFilter() {
        return runtimeOnly != null;
    }

    public boolean hasGlobalFilter() {
        return global != null;
    }

    public boolean hasParamsFilter() {
        return hasParams != null;
    }

    public boolean hasReturnValueFilter() {
        return hasReturnValue != null;
    }

    public boolean includesType(CatalogType catalogType) {
        return !hasTypeFilter() || types.contains(catalogType.label());
    }

    // ------------------------------------------------------ internal

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

    private static final Map<String, String> VALID_SORT_FIELDS = Map.of(
            "name", "name",
            "type", "type",
            "resourceaddress", "resourceAddress",
            "stability", "stability");

    private static String parseSort(String sort, List<String> types) {
        if (sort != null && !sort.isBlank()) {
            String canonical = VALID_SORT_FIELDS.get(sort.strip().toLowerCase());
            if (canonical != null) {
                return canonical;
            }
        }
        if (types.size() == 1 && types.contains("resource")) {
            return "resourceAddress";
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
