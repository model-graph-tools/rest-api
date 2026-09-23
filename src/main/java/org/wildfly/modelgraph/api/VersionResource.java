package org.wildfly.modelgraph.api;

import java.util.Optional;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;

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
