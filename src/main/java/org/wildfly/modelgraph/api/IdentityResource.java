package org.wildfly.modelgraph.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/identity")
@Produces(MediaType.APPLICATION_JSON)
public class IdentityResource {

    @Inject
    ModelGraphRepository repository;

    @GET
    public IdentityResponse identity() {
        return repository.identity();
    }
}
