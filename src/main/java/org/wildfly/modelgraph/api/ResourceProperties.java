package org.wildfly.modelgraph.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Properties specific to resource results")
public record ResourceProperties(
        @Schema(description = "Whether the resource is a singleton")
        boolean singleton,

        @Schema(description = "Names of capabilities declared by this resource (empty list if none)")
        List<String> capabilities,

        @Schema(description = "Whether the resource defines attribute groups")
        boolean definesAttrGroups)
        implements Properties {
}
