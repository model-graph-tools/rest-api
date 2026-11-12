package org.wildfly.modelgraph.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "REST API build information")
public record VersionResponse(
        @Schema(description = "API version", example = "0.3.3")
        String version,
        @Schema(description = "Build timestamp")
        String buildTime,
        @Schema(description = "Git commit hash")
        String commit,
        @Schema(description = "Git tag")
        String tag) {
}
