package org.wildfly.modelgraph.api.search;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Wrapper for the list of search results returned by the search endpoint. */
@RegisterForReflection
@Schema(description = "Search results wrapper")
public record SearchResponse(
        @Schema(description = "List of matching results across all types")
        List<SearchResult> results) {
}
