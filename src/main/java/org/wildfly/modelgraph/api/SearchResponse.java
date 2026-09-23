package org.wildfly.modelgraph.api;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record SearchResponse(List<SearchResult> results) {
}
