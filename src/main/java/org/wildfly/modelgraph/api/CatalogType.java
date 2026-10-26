package org.wildfly.modelgraph.api;

import java.util.Set;

public enum CatalogType {

    RESOURCE("resource"),
    ATTRIBUTE("attribute"),
    OPERATION("operation"),
    CAPABILITY("capability");

    private static final Set<String> ALL_FACETS = Set.of("type", "stability");

    private final String label;

    CatalogType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public Set<String> applicableFacets() {
        return ALL_FACETS;
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
