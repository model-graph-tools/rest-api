package org.wildfly.modelgraph.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/api/search")
@Produces(MediaType.APPLICATION_JSON)
public class SearchResource {

    private static final int DEFAULT_LIMIT = 10;

    @Inject
    ModelGraphRepository repository;

    @GET
    public Response search(@QueryParam("q") String query, @QueryParam("limit") Integer limit) {
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
