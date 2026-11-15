package org.wildfly.modelgraph.api.capability;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Full capability detail including which resources declare it and which attributes reference it. */
@RegisterForReflection
@Schema(description = "Detailed capability information including declaring resources and referencing attributes")
public record CapabilityDetailResponse(
        @Schema(description = "Capability name", examples = {"org.wildfly.data-source"})
        String name,
        @Schema(description = "Stability level", examples = {"stable"}, enumeration = {"default", "community", "preview", "experimental"})
        String stability,
        @Schema(description = "Resources that declare this capability")
        List<DeclaredBy> declaredBy,
        @Schema(description = "Attributes that reference this capability")
        List<CapabilityReference> referencedBy) {

    @RegisterForReflection
    @Schema(description = "A resource that declares a capability")
    public record DeclaredBy(
            @Schema(description = "Resource name", examples = {"data-source"})
            String resourceName,
            @Schema(description = "Full resource address", examples = {"/subsystem=datasources/data-source=*"})
            String resourceAddress) {
    }
}
