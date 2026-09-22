package org.wildfly.modelgraph.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/capability")
@Produces(MediaType.APPLICATION_JSON)
public class CapabilityResource {

    @Inject
    ModelGraphRepository repository;

    @GET
    @Path("/{name}/references")
    public CapabilityReferencesResponse references(@PathParam("name") String name) {
        var refs = repository.capabilityReferences(name);
        return new CapabilityReferencesResponse(name, refs);
    }
}
