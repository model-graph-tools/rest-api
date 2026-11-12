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

@Path("/api/resource")
@Produces(MediaType.APPLICATION_JSON)
public class ResourceDetailResource {

    @Inject
    ModelGraphRepository repository;

    @GET
    @Operation(
            summary = "Get resource details",
            description = "Returns full details of a resource including child resources, attributes, operations, and capabilities, all sorted alphabetically by name")
    @APIResponse(
            responseCode = "200",
            description = "Resource found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ResourceDetailResponse.class)))
    @APIResponse(responseCode = "400", description = "Missing address parameter")
    @APIResponse(responseCode = "404", description = "Resource not found")
    public Response detail(
            @Parameter(description = "Full resource address", example = "/subsystem=io/buffer-pool=*", required = true)
            @QueryParam("address") String address) {
        if (address == null || address.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Query parameter 'address' is required"))
                    .build();
        }
        return repository.resourceDetail(address)
                .<Response>map(detail -> Response.ok(detail).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Resource not found", "address", address))
                        .build());
    }
}
