package org.wildfly.modelgraph.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

@Path("/api/identity")
@Produces(MediaType.APPLICATION_JSON)
public class IdentityResource {

    @Inject
    ModelGraphRepository repository;

    @GET
    @Operation(
            summary = "Get WildFly identity",
            description = "Returns the feature pack identity metadata from the management model graph")
    @APIResponse(
            responseCode = "200",
            description = "Identity information",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = IdentityResponse.class)))
    public IdentityResponse identity() {
        return repository.identity();
    }
}
