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

@Path("/api/attribute")
@Produces(MediaType.APPLICATION_JSON)
public class AttributeDetailResource {

    @Inject
    ModelGraphRepository repository;

    @GET
    @Operation(
            summary = "Get attribute details",
            description = "Returns full details of an attribute including capability references, required/alternative attributes, and sub-attributes")
    @APIResponse(
            responseCode = "200",
            description = "Attribute found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = AttributeDetailResponse.class)))
    @APIResponse(responseCode = "400", description = "Missing required parameters")
    @APIResponse(responseCode = "404", description = "Attribute not found")
    public Response detail(
            @Parameter(description = "Owning resource address", example = "/subsystem=datasources/data-source=*", required = true)
            @QueryParam("resource") String resource,
            @Parameter(description = "Attribute name", example = "connection-url", required = true)
            @QueryParam("name") String name) {
        if (resource == null || resource.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Query parameter 'resource' is required"))
                    .build();
        }
        if (name == null || name.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Query parameter 'name' is required"))
                    .build();
        }
        return repository.attributeDetail(resource, name)
                .<Response>map(detail -> Response.ok(detail).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Attribute not found", "resource", resource, "name", name))
                        .build());
    }
}
