package org.wildfly.modelgraph.api;

import java.util.Optional;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

@Path("/api/version")
@Produces(MediaType.APPLICATION_JSON)
public class VersionResource {

    @ConfigProperty(name = "quarkus.application.version")
    String version;

    @ConfigProperty(name = "rest-api.build-time")
    Optional<String> buildTime;

    @ConfigProperty(name = "rest-api.commit")
    Optional<String> commit;

    @ConfigProperty(name = "rest-api.tag")
    Optional<String> tag;

    @GET
    @Operation(
            summary = "Get API version",
            description = "Returns the REST API build information including version, build time, commit, and tag")
    @APIResponse(
            responseCode = "200",
            description = "API version information",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = VersionResponse.class)))
    public VersionResponse version() {
        return new VersionResponse(
                version,
                emptyToNull(buildTime),
                emptyToNull(commit),
                emptyToNull(tag));
    }

    private String emptyToNull(Optional<String> value) {
        return value.filter(s -> !s.isBlank()).orElse(null);
    }
}
