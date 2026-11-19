package org.wildfly.modelgraph.api.catalog;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** Type-specific properties for capability catalog results. */
@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Properties specific to capability results")
public record CapabilityProperties(
        @Schema(description = "Resources that declare this capability, with per-declaration stability")
        List<DeclaringResource> declaringResources)
        implements Properties {
}
