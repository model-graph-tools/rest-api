package org.wildfly.modelgraph.api;

import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@Schema(description = "Paginated catalog response with facet counts for filtering")
public record CatalogResponse(
        @Schema(description = "Paginated list of matching catalog results")
        List<CatalogResult> results,

        @Schema(description = "Facet counts keyed by facet name, each containing value-to-count mappings. Counts reflect the result set with all other active filters applied but without the facet's own filter.")
        Map<String, Map<String, Long>> facets,

        @Schema(description = "Total number of matching results across all pages", example = "213")
        int total,

        @Schema(description = "Page size used for this response", example = "50")
        int limit,

        @Schema(description = "Pagination offset used for this response", example = "0")
        int offset) {
}
