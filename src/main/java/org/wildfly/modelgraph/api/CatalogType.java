package org.wildfly.modelgraph.api;

import java.util.Set;

public enum CatalogType {

    RESOURCE("resource", Set.of("type", "stability")),
    ATTRIBUTE("attribute", Set.of("type", "stability",
            "dataType", "accessType", "storage", "restart",
            "required", "nillable", "expressions",
            "unit", "hasAllowed")),
    OPERATION("operation", Set.of("type", "stability")),
    CAPABILITY("capability", Set.of("type", "stability"));

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

    public static CatalogType fromLabel(String label) {
        for (CatalogType type : values()) {
            if (type.label.equals(label)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown catalog type: " + label);
    }
}
