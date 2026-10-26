package org.wildfly.modelgraph.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Properties specific to operation results")
public record OperationProperties(
        @Schema(description = "Whether the operation is read-only")
        boolean readOnly,

        @Schema(description = "Whether the operation is runtime-only")
        boolean runtimeOnly,

        @Schema(description = "Whether the operation is global")
        boolean global,

        @Schema(description = "Whether the operation has parameters")
        boolean hasParams,

        @Schema(description = "Whether the operation has a return value")
        boolean hasReturnValue) implements Properties {
}
