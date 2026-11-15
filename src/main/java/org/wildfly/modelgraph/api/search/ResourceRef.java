package org.wildfly.modelgraph.api.search;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A name/address pair identifying a resource, used within capability search results to indicate which resources provide a capability. */
@RegisterForReflection
@Schema(description = "Reference to a resource")
public record ResourceRef(
        @Schema(description = "Resource name", example = "data-source")
        String name,
        @Schema(description = "Resource address", example = "/subsystem=datasources/data-source=*")
        String address) {
}
