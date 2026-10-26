package org.wildfly.modelgraph.api;

import java.util.Set;

public enum CatalogType {

    RESOURCE("resource", Set.of("type", "stability", "deprecated", "singleton", "declaresCap", "definesAttrGroups")),
    ATTRIBUTE("attribute", Set.of("type", "stability", "deprecated",
            "dataType", "accessType", "storage", "restart",
            "required", "nillable", "expressions",
            "unit", "hasAllowed",
            "sensitive", "hasRelationships", "refsCap")),
    OPERATION("operation", Set.of("type", "stability", "deprecated",
            "readOnly", "runtimeOnly", "global", "hasParams", "hasReturnValue")),
    CAPABILITY("capability", Set.of("type", "stability", "deprecated"));

    private static final Set<String> COMMON_FACETS = Set.of("type", "stability", "deprecated");

    private final String label;
    private final Set<String> applicableFacets;

    CatalogType(String label, Set<String> applicableFacets) {
        this.label = label;
        this.applicableFacets = applicableFacets;
    }

    public String label() {
        return label;
    }

    public Set<String> applicableFacets() {
        return applicableFacets;
    }

    public static Set<String> commonFacets() {
        return COMMON_FACETS;
    }

    public static CatalogType fromLabel(String label) {
        for (CatalogType type : values()) {
            if (type.label.equals(label)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown catalog type: " + label);
    }
}
