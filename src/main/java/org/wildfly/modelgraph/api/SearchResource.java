package org.wildfly.modelgraph.api;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

@Path("/api/search")
@Produces(MediaType.APPLICATION_JSON)
public class SearchResource {

    private static final int DEFAULT_LIMIT = 10;

    @Inject
    ModelGraphRepository repository;

    @GET
    @Operation(
            summary = "Search the management model",
            description = "Unified full-text search across resources, attributes, capabilities, and operations. Excludes deployment-scoped resources.")
    @APIResponse(
            responseCode = "200",
            description = "Search results",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = SearchResponse.class)))
    @APIResponse(responseCode = "400", description = "Missing query parameter")
    public Response search(
            @Parameter(description = "Search term", example = "datasource", required = true)
            @QueryParam("q") String query,
            @Parameter(description = "Maximum number of results per type", example = "10",
                    schema = @Schema(type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.INTEGER, defaultValue = "10"))
            @QueryParam("limit") Integer limit) {
        if (query == null || query.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Query parameter 'q' is required"))
                    .build();
        }
        int effectiveLimit = (limit != null && limit > 0) ? limit : DEFAULT_LIMIT;
        var results = repository.search(query.strip(), effectiveLimit);
        return Response.ok(new SearchResponse(results)).build();
    }
}
