package org.wildfly.modelgraph.api;

import java.util.Set;

public enum CatalogType {

    RESOURCE("resource", Set.of("type", "stability", "deprecated", "singleton", "declaresCap", "definesAttrGroups")),
    ATTRIBUTE("attribute", Set.of("type", "stability", "deprecated")),
    OPERATION("operation", Set.of("type", "stability", "deprecated")),
    CAPABILITY("capability", Set.of("type", "stability", "deprecated"));

    private static final Set<String> COMMON_FACETS = Set.of("type", "stability", "deprecated");

    private final String label;
    private final Set<String> facets;

    CatalogType(String label, Set<String> facets) {
        this.label = label;
        this.facets = facets;
    }

    public String label() {
        return label;
    }

    public Set<String> applicableFacets() {
        return facets;
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
