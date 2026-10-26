package org.wildfly.modelgraph.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/catalog")
@Produces(MediaType.APPLICATION_JSON)
public class CatalogResource {

    @Inject
    CatalogRepository repository;

    @GET
    public Response catalog(
            @QueryParam("q") String q,
            @QueryParam("type") String type,
            @QueryParam("stability") String stability,
            @QueryParam("deprecated") String deprecated,
            @QueryParam("scope") String scope,
            @QueryParam("singleton") Boolean singleton,
            @QueryParam("declaresCap") String declaresCap,
            @QueryParam("definesAttrGroups") Boolean definesAttrGroups,
            @QueryParam("dataType") String dataType,
            @QueryParam("accessType") String accessType,
            @QueryParam("storage") String storage,
            @QueryParam("restart") String restart,
            @QueryParam("required") Boolean required,
            @QueryParam("nillable") Boolean nillable,
            @QueryParam("expressions") Boolean expressions,
            @QueryParam("unit") String unit,
            @QueryParam("hasAllowed") Boolean hasAllowed,
            @QueryParam("readOnly") Boolean readOnly,
            @QueryParam("runtimeOnly") Boolean runtimeOnly,
            @QueryParam("global") Boolean global,
            @QueryParam("hasParams") Boolean hasParams,
            @QueryParam("hasReturnValue") Boolean hasReturnValue,
            @QueryParam("sort") String sort,
            @QueryParam("order") String order,
            @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset) {

        CatalogFilter filter = CatalogFilter.of(q, type, stability, deprecated, scope,
                singleton, declaresCap, definesAttrGroups,
                dataType, accessType, storage, restart, required, nillable, expressions,
                unit, hasAllowed,
                readOnly, runtimeOnly, global, hasParams, hasReturnValue,
                sort, order, limit, offset);
        var results = repository.search(filter);
        int total = repository.total(filter);
        var facets = repository.facetCounts(filter);
        return Response.ok(new CatalogResponse(results, facets, total, filter.limit(), filter.offset())).build();
    }
}
