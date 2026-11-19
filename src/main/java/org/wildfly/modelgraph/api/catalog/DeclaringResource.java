package org.wildfly.modelgraph.api.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** A resource that declares a capability, with the stability level reported by that resource. */
@RegisterForReflection
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "A resource that declares a capability")
public record DeclaringResource(
        @Schema(description = "The resource address")
        String address,
        @Schema(description = "The stability level reported by this resource's capability declaration, null if not reported")
        String stability) {
}
