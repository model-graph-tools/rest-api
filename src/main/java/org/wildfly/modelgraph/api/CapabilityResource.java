package org.wildfly.modelgraph.api;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/capability")
@Produces(MediaType.APPLICATION_JSON)
public class CapabilityResource {

    @Inject
    ModelGraphRepository repository;

    @GET
    @Path("/{name}")
    public Response detail(@PathParam("name") String name) {
        return repository.capabilityDetail(name)
                .<Response>map(detail -> Response.ok(detail).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Capability not found", "name", name))
                        .build());
    }

    @GET
    @Path("/{name}/references")
    public CapabilityReferencesResponse references(@PathParam("name") String name) {
        var refs = repository.capabilityReferences(name);
        return new CapabilityReferencesResponse(name, refs);
    }
}
