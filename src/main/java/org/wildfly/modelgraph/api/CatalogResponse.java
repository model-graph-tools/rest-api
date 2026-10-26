package org.wildfly.modelgraph.api;

import java.util.List;
import java.util.Map;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record CatalogResponse(
        List<CatalogResult> results,
        Map<String, Map<String, Long>> facets,
        int total,
        int limit,
        int offset) {
}
