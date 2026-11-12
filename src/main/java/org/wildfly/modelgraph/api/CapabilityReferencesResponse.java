package org.wildfly.modelgraph.api;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@RegisterForReflection
@Schema(description = "Attributes that reference a capability")
public record CapabilityReferencesResponse(
        @Schema(description = "Capability name", example = "org.wildfly.data-source")
        String capability,
        @Schema(description = "List of referencing attributes")
        List<CapabilityReference> references) {
}
