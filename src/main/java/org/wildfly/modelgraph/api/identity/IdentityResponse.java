package org.wildfly.modelgraph.api.identity;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Feature pack identity from the graph's {@code Identity} node, including Maven coordinates and license info. */
@RegisterForReflection
@Schema(description = "WildFly feature pack identity metadata")
public record IdentityResponse(
        @Schema(description = "Feature pack identifier", examples = {"wildfly"})
        String identifier,
        @Schema(description = "Maven group ID", examples = {"org.wildfly"})
        String groupId,
        @Schema(description = "Maven artifact ID", examples = {"wildfly-galleon-pack"})
        String artifactId,
        @Schema(description = "Feature pack name", examples = {"WildFly"})
        String name,
        @Schema(description = "WildFly version", examples = {"42.0.0.Final"})
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
