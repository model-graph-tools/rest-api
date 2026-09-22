package org.wildfly.modelgraph.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/version")
@Produces(MediaType.APPLICATION_JSON)
public class VersionResource {

    @Inject
    ModelGraphRepository repository;

    @GET
    public VersionResponse version() {
        return repository.version();
    }
}
