package org.wildfly.modelgraph.api;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@RegisterForReflection
@Schema(description = "An attribute that references a capability")
public record CapabilityReference(
        @Schema(description = "Attribute name", example = "datasource")
        String attributeName,
        @Schema(description = "Attribute stability level", example = "stable")
        String attributeStability,
        @Schema(description = "Owning resource name", example = "mail-session")
        String resourceName,
        @Schema(description = "Owning resource address", example = "/subsystem=mail/mail-session=*")
        String resourceAddress,
        @Schema(description = "Owning resource stability level", example = "stable")
        String resourceStability) {
}
