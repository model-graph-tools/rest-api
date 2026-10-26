package org.wildfly.modelgraph.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

@Path("/api/catalog")
@Produces(MediaType.APPLICATION_JSON)
public class CatalogResource {

    @Inject
    CatalogRepository repository;

    @GET
    @Operation(
            summary = "Faceted search across the management model",
            description = "Returns paginated results with facet counts for filtering resources, attributes, operations, and capabilities.")
    @APIResponse(
            responseCode = "200",
            description = "Paginated results with facet counts",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = CatalogResponse.class)))
    public Response catalog(
            @Parameter(
                    name = "q",
                    description = "Fulltext search on name only (uses mgt_search fulltext index)",
                    example = "pool",
                    schema = @Schema(type = SchemaType.STRING))
            @QueryParam("q") String q,

            @Parameter(
                    name = "type",
                    description = "Node type filter. Comma-separated values with OR semantics.",
                    example = "resource,attribute",
                    schema = @Schema(type = SchemaType.STRING, enumeration = {"resource", "attribute", "operation", "capability"}))
            @QueryParam("type") String type,

            @Parameter(
                    name = "stability",
                    description = "Stability level filter. Comma-separated values with OR semantics.",
                    example = "default,community",
                    schema = @Schema(type = SchemaType.STRING, enumeration = {"default", "community", "preview", "experimental"}))
            @QueryParam("stability") String stability,

            @Parameter(
                    name = "deprecated",
                    description = "Deprecation status: 'yes' for only deprecated elements, 'no' for only non-deprecated elements",
                    example = "no",
                    schema = @Schema(type = SchemaType.STRING, enumeration = {"yes", "no"}))
            @QueryParam("deprecated") String deprecated,

            @Parameter(
                    name = "scope",
                    description = "Resource address prefix — subtree filter for all types. For resources: address STARTS WITH scope. For attributes/operations: parent resource address starts with scope. For capabilities: declaring resource address starts with scope.",
                    example = "/subsystem=datasources",
                    schema = @Schema(type = SchemaType.STRING))
            @QueryParam("scope") String scope,

            @Parameter(
                    name = "singleton",
                    description = "Resource: singleton flag. Only applies to resources.",
                    example = "true",
                    schema = @Schema(type = SchemaType.BOOLEAN))
            @QueryParam("singleton") Boolean singleton,

            @Parameter(
                    name = "declaresCap",
                    description = "Resource: capability declaration filter. Three modes: 'yes' (has any capability), 'no' (has no capability), or 'contains:<text>' (capability name contains substring). Only applies to resources.",
                    example = "contains:io",
                    schema = @Schema(type = SchemaType.STRING))
            @QueryParam("declaresCap") String declaresCap,

            @Parameter(
                    name = "definesAttrGroups",
                    description = "Resource: defines attribute groups. Only applies to resources.",
                    example = "true",
                    schema = @Schema(type = SchemaType.BOOLEAN))
            @QueryParam("definesAttrGroups") Boolean definesAttrGroups,

            @Parameter(
                    name = "dataType",
                    description = "Attribute: data type filter. Comma-separated values with OR semantics. The value 'complex' matches attributes with CONSISTS_OF sub-attributes. Only applies to attributes.",
                    example = "string,boolean",
                    schema = @Schema(type = SchemaType.STRING, enumeration = {"string", "boolean", "int", "long", "object", "list", "double", "bytes", "complex"}))
            @QueryParam("dataType") String dataType,

            @Parameter(
                    name = "accessType",
                    description = "Attribute: access type filter. Comma-separated values with OR semantics. Only applies to attributes.",
                    example = "read-write",
                    schema = @Schema(type = SchemaType.STRING, enumeration = {"read-write", "read-only", "metric"}))
            @QueryParam("accessType") String accessType,

            @Parameter(
                    name = "storage",
                    description = "Attribute: storage type filter. Comma-separated values with OR semantics. Only applies to attributes.",
                    example = "configuration",
                    schema = @Schema(type = SchemaType.STRING, enumeration = {"configuration", "runtime"}))
            @QueryParam("storage") String storage,

            @Parameter(
                    name = "restart",
                    description = "Attribute: restart policy filter. Comma-separated values with OR semantics. Only applies to attributes.",
                    example = "all-services",
                    schema = @Schema(type = SchemaType.STRING, enumeration = {"all-services", "no-services", "resource-services", "jvm"}))
            @QueryParam("restart") String restart,

            @Parameter(
                    name = "required",
                    description = "Attribute: required flag. Only applies to attributes.",
                    example = "true",
                    schema = @Schema(type = SchemaType.BOOLEAN))
            @QueryParam("required") Boolean required,

            @Parameter(
                    name = "nillable",
                    description = "Attribute: nillable flag. Only applies to attributes.",
                    example = "true",
                    schema = @Schema(type = SchemaType.BOOLEAN))
            @QueryParam("nillable") Boolean nillable,

            @Parameter(
                    name = "expressions",
                    description = "Attribute: expressions allowed flag. Only applies to attributes.",
                    example = "true",
                    schema = @Schema(type = SchemaType.BOOLEAN))
            @QueryParam("expressions") Boolean expressions,

            @Parameter(
                    name = "unit",
                    description = "Attribute: measurement unit filter. Comma-separated values with OR semantics. Only applies to attributes.",
                    example = "milliseconds",
                    schema = @Schema(type = SchemaType.STRING, enumeration = {"milliseconds", "bytes", "seconds", "per_second", "nanoseconds", "minutes", "percentage", "days"}))
            @QueryParam("unit") String unit,

            @Parameter(
                    name = "hasAllowed",
                    description = "Attribute: has allowed values. Only applies to attributes.",
                    example = "true",
                    schema = @Schema(type = SchemaType.BOOLEAN))
            @QueryParam("hasAllowed") Boolean hasAllowed,

            @Parameter(
                    name = "sensitive",
                    description = "Attribute: has IS_SENSITIVE relationship. Only applies to attributes.",
                    example = "true",
                    schema = @Schema(type = SchemaType.BOOLEAN))
            @QueryParam("sensitive") Boolean sensitive,

            @Parameter(
                    name = "hasRelationships",
                    description = "Attribute: has REQUIRES or ALTERNATIVE dependencies. Only applies to attributes.",
                    example = "true",
                    schema = @Schema(type = SchemaType.BOOLEAN))
            @QueryParam("hasRelationships") Boolean hasRelationships,

            @Parameter(
                    name = "refsCap",
                    description = "Attribute: capability reference filter. Three modes: 'yes' (references any capability), 'no' (references no capability), or 'contains:<text>' (capability name contains substring). Only applies to attributes.",
                    example = "yes",
                    schema = @Schema(type = SchemaType.STRING))
            @QueryParam("refsCap") String refsCap,

            @Parameter(
                    name = "readOnly",
                    description = "Operation: read-only flag. Only applies to operations.",
                    example = "true",
                    schema = @Schema(type = SchemaType.BOOLEAN))
            @QueryParam("readOnly") Boolean readOnly,

            @Parameter(
                    name = "runtimeOnly",
                    description = "Operation: runtime-only flag. Only applies to operations.",
                    example = "true",
                    schema = @Schema(type = SchemaType.BOOLEAN))
            @QueryParam("runtimeOnly") Boolean runtimeOnly,

            @Parameter(
                    name = "global",
                    description = "Operation: global flag. Only applies to operations.",
                    example = "false",
                    schema = @Schema(type = SchemaType.BOOLEAN))
            @QueryParam("global") Boolean global,

            @Parameter(
                    name = "hasParams",
                    description = "Operation: has parameters. Only applies to operations.",
                    example = "true",
                    schema = @Schema(type = SchemaType.BOOLEAN))
            @QueryParam("hasParams") Boolean hasParams,

            @Parameter(
                    name = "hasReturnValue",
                    description = "Operation: has return value. Only applies to operations.",
                    example = "true",
                    schema = @Schema(type = SchemaType.BOOLEAN))
            @QueryParam("hasReturnValue") Boolean hasReturnValue,

            @Parameter(
                    name = "sort",
                    description = "Sort field. When sorting by address, null values sort last regardless of direction.",
                    example = "name",
                    schema = @Schema(type = SchemaType.STRING, defaultValue = "name", enumeration = {"name", "type", "address", "stability"}))
            @QueryParam("sort") String sort,

            @Parameter(
                    name = "order",
                    description = "Sort direction",
                    example = "asc",
                    schema = @Schema(type = SchemaType.STRING, defaultValue = "asc", enumeration = {"asc", "desc"}))
            @QueryParam("order") String order,

            @Parameter(
                    name = "limit",
                    description = "Page size",
                    example = "50",
                    schema = @Schema(type = SchemaType.INTEGER, defaultValue = "50"))
            @QueryParam("limit") Integer limit,

            @Parameter(
                    name = "offset",
                    description = "Pagination offset",
                    example = "0",
                    schema = @Schema(type = SchemaType.INTEGER, defaultValue = "0"))
            @QueryParam("offset") Integer offset) {

        CatalogFilter filter = CatalogFilter.of(q, type, stability, deprecated, scope,
                singleton, declaresCap, definesAttrGroups,
                dataType, accessType, storage, restart, required, nillable, expressions,
                unit, hasAllowed,
                sensitive, hasRelationships, refsCap,
                readOnly, runtimeOnly, global, hasParams, hasReturnValue,
                sort, order, limit, offset);
        var results = repository.search(filter);
        int total = repository.total(filter);
        var facets = repository.facetCounts(filter);
        return Response.ok(new CatalogResponse(results, facets, total, filter.limit(), filter.offset())).build();
    }
}
