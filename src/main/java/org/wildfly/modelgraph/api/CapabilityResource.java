package org.wildfly.modelgraph.api;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

@Path("/api/capability")
@Produces(MediaType.APPLICATION_JSON)
public class CapabilityResource {

    @Inject
    ModelGraphRepository repository;

    @GET
    @Path("/{name}")
    @Operation(
            summary = "Get capability details",
            description = "Returns full details of a capability including declaring resources and referencing attributes, sorted alphabetically by name")
    @APIResponse(
            responseCode = "200",
            description = "Capability found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = CapabilityDetailResponse.class)))
    @APIResponse(
            responseCode = "404",
            description = "Capability not found")
    public Response detail(
            @Parameter(description = "Capability name", example = "org.wildfly.data-source", required = true)
            @PathParam("name") String name) {
        return repository.capabilityDetail(name)
                .<Response>map(detail -> Response.ok(detail).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Capability not found", "name", name))
                        .build());
    }

    @GET
    @Path("/{name}/references")
    @Operation(
            summary = "Get capability references",
            description = "Returns attributes that reference a capability")
    @APIResponse(
            responseCode = "200",
            description = "Capability references",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = CapabilityReferencesResponse.class)))
    public CapabilityReferencesResponse references(
            @Parameter(description = "Capability name", example = "org.wildfly.data-source", required = true)
            @PathParam("name") String name) {
        var refs = repository.capabilityReferences(name);
        return new CapabilityReferencesResponse(name, refs);
    }
}
