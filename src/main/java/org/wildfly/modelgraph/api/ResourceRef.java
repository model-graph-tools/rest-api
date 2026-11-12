package org.wildfly.modelgraph.api;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@RegisterForReflection
@Schema(description = "Reference to a resource")
public record ResourceRef(
        @Schema(description = "Resource name", example = "data-source")
        String name,
        @Schema(description = "Resource address", example = "/subsystem=datasources/data-source=*")
        String address) {
}
