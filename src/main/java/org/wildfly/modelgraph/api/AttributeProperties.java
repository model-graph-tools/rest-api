package org.wildfly.modelgraph.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Properties specific to attribute results")
public record AttributeProperties(
        @Schema(description = "Data type of the attribute", example = "string", enumeration = {"string", "boolean", "int", "long", "object", "list", "double", "bytes", "complex"})
        String dataType,

        @Schema(description = "Access type of the attribute", example = "read-write", enumeration = {"read-write", "read-only", "metric"})
        String accessType,

        @Schema(description = "Storage type of the attribute", example = "configuration", enumeration = {"configuration", "runtime"})
        String storage,

        @Schema(description = "Restart policy for the attribute", example = "all-services", enumeration = {"all-services", "no-services", "resource-services", "jvm"})
        String restart,

        @Schema(description = "Whether the attribute is required")
        boolean required,

        @Schema(description = "Whether the attribute is nillable")
        boolean nillable,

        @Schema(description = "Whether expressions are allowed")
        boolean expressions,

        @Schema(description = "Whether the attribute has an IS_SENSITIVE relationship")
        boolean sensitive,

        @Schema(description = "Measurement unit of the attribute", example = "milliseconds", enumeration = {"milliseconds", "bytes", "seconds", "per_second", "nanoseconds", "minutes", "percentage", "days"})
        String unit) implements Properties {
}
