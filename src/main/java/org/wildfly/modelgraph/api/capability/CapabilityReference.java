package org.wildfly.modelgraph.api.capability;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** An attribute that references a capability, used in capability detail and references responses. */
@RegisterForReflection
@Schema(description = "An attribute that references a capability")
public record CapabilityReference(
        @Schema(description = "Attribute name", examples = {"datasource"})
        String attributeName,
        @Schema(description = "Attribute stability level", examples = {"stable"})
        String attributeStability,
        @Schema(description = "Owning resource name", examples = {"mail-session"})
        String resourceName,
        @Schema(description = "Owning resource address", examples = {"/subsystem=mail/mail-session=*"})
        String resourceAddress,
        @Schema(description = "Owning resource stability level", examples = {"stable"})
        String resourceStability) {
}
