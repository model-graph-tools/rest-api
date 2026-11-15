package org.wildfly.modelgraph.api.catalog;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** Response payload for the catalog endpoint, containing paginated results, facet counts, and pagination metadata. */
@RegisterForReflection
@Schema(description = "Paginated catalog response with facet counts for filtering")
public record CatalogResponse(
        @Schema(description = "Paginated list of matching catalog results")
        List<CatalogResult> results,

        @Schema(description = "Facet counts for filtering. Counts reflect the result set with all other active filters applied but without the facet's own filter.")
        CatalogFacets facets,

        @Schema(description = "Total number of matching results across all pages", example = "213")
        int total,

        @Schema(description = "Page size used for this response", example = "50")
        int limit,

        @Schema(description = "Pagination offset used for this response", example = "0")
        int offset) {
}
