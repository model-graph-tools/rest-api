package org.wildfly.modelgraph.api;

import java.util.List;

public record SearchResponse(List<SearchResult> results) {
}
