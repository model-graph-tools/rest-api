package org.wildfly.modelgraph.api;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@RegisterForReflection
@Schema(description = "Detailed capability information including declaring resources and referencing attributes")
public record CapabilityDetailResponse(
        @Schema(description = "Capability name", example = "org.wildfly.data-source")
        String name,
        @Schema(description = "Stability level", example = "stable", enumeration = {"default", "community", "preview", "experimental"})
        String stability,
        @Schema(description = "Resources that declare this capability")
        List<DeclaredBy> declaredBy,
        @Schema(description = "Attributes that reference this capability")
        List<CapabilityReference> referencedBy) {

    @RegisterForReflection
    @Schema(description = "A resource that declares a capability")
    public record DeclaredBy(
            @Schema(description = "Resource name", example = "data-source")
            String resourceName,
            @Schema(description = "Full resource address", example = "/subsystem=datasources/data-source=*")
            String resourceAddress) {
    }
}
