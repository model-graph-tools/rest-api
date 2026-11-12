package org.wildfly.modelgraph.api;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@RegisterForReflection
@Schema(description = "WildFly feature pack identity metadata")
public record IdentityResponse(
        @Schema(description = "Feature pack identifier", example = "wildfly")
        String identifier,
        @Schema(description = "Maven group ID", example = "org.wildfly")
        String groupId,
        @Schema(description = "Maven artifact ID", example = "wildfly-galleon-pack")
        String artifactId,
        @Schema(description = "Feature pack name", example = "WildFly")
        String name,
        @Schema(description = "WildFly version", example = "42.0.0.Final")
        String version,
        @Schema(description = "Feature pack type")
        String type,
        @Schema(description = "Feature pack description")
        String description,
        @Schema(description = "Project URL")
        String url,
        @Schema(description = "Source control URL")
        String scmUrl,
        @Schema(description = "License information")
        String licenses) {
}
