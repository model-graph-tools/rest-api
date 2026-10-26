package org.wildfly.modelgraph.api;

import java.util.Arrays;
import java.util.List;

public final class CatalogFilter {

    private static final int DEFAULT_LIMIT = 50;
    private static final int DEFAULT_OFFSET = 0;

    private final List<String> types;
    private final List<String> stabilities;
    private final List<String> dataTypes;
    private final List<String> accessTypes;
    private final List<String> storageValues;
    private final List<String> restartValues;
    private final Boolean required;
    private final Boolean nillable;
    private final Boolean expressions;
    private final List<String> units;
    private final Boolean hasAllowed;
    private final int limit;
    private final int offset;

    private CatalogFilter(List<String> types, List<String> stabilities,
            List<String> dataTypes, List<String> accessTypes, List<String> storageValues,
            List<String> restartValues, Boolean required, Boolean nillable, Boolean expressions,
            List<String> units, Boolean hasAllowed, int limit, int offset) {
        this.types = types;
        this.stabilities = stabilities;
        this.dataTypes = dataTypes;
        this.accessTypes = accessTypes;
        this.storageValues = storageValues;
        this.restartValues = restartValues;
        this.required = required;
        this.nillable = nillable;
        this.expressions = expressions;
        this.units = units;
        this.hasAllowed = hasAllowed;
        this.limit = limit;
        this.offset = offset;
    }

    public static CatalogFilter of(String type, String stability,
            String dataType, String accessType, String storage, String restart,
            Boolean required, Boolean nillable, Boolean expressions,
            String unit, Boolean hasAllowed,
            Integer limit, Integer offset) {
        List<String> types = parseMultiValue(type);
        List<String> stabilities = parseMultiValue(stability);
        List<String> dataTypes = parseMultiValue(dataType);
        List<String> accessTypes = parseMultiValue(accessType);
        List<String> storageValues = parseMultiValue(storage);
        List<String> restartValues = parseMultiValue(restart);
        List<String> units = parseMultiValue(unit);
        int effectiveLimit = (limit != null && limit > 0) ? limit : DEFAULT_LIMIT;
        int effectiveOffset = (offset != null && offset >= 0) ? offset : DEFAULT_OFFSET;
        return new CatalogFilter(types, stabilities, dataTypes, accessTypes, storageValues,
                restartValues, required, nillable, expressions, units, hasAllowed,
                effectiveLimit, effectiveOffset);
    }

    public List<String> types() {
        return types;
    }

    public List<String> stabilities() {
        return stabilities;
    }

    public List<String> dataTypes() {
        return dataTypes;
    }

    public List<String> accessTypes() {
        return accessTypes;
    }

    public List<String> storageValues() {
        return storageValues;
    }

    public List<String> restartValues() {
        return restartValues;
    }

    public Boolean required() {
        return required;
    }

    public Boolean nillable() {
        return nillable;
    }

    public Boolean expressions() {
        return expressions;
    }

    public List<String> units() {
        return units;
    }

    public Boolean hasAllowed() {
        return hasAllowed;
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

    public boolean hasAnyAttributeFilter() {
        return hasDataTypeFilter() || hasAccessTypeFilter() || hasStorageFilter()
                || hasRestartFilter() || hasRequiredFilter() || hasNillableFilter()
                || hasExpressionsFilter() || hasUnitFilter() || hasHasAllowedFilter();
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
}
