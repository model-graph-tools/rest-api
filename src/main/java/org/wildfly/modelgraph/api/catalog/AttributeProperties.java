package org.wildfly.modelgraph.api.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** Type-specific properties for attribute catalog results. */
@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Properties specific to attribute results")
public record AttributeProperties(
        @Schema(description = "Data type of the attribute", examples = {"string"}, enumeration = {"string", "boolean", "int", "long", "object", "list", "double", "bytes", "complex"})
        String dataType,

        @Schema(description = "Access type of the attribute", examples = {"read-write"}, enumeration = {"read-write", "read-only", "metric"})
        String accessType,

        @Schema(description = "Storage type of the attribute", examples = {"configuration"}, enumeration = {"configuration", "runtime"})
        String storage,

        @Schema(description = "Restart policy for the attribute", examples = {"all-services"}, enumeration = {"all-services", "no-services", "resource-services", "jvm"})
        String restart,

        @Schema(description = "Whether the attribute is required")
        boolean required,

        @Schema(description = "Whether the attribute is nillable")
        boolean nillable,

        @Schema(description = "Whether expressions are allowed")
        boolean expressions,

        @Schema(description = "Whether the attribute has an IS_SENSITIVE relationship")
        boolean sensitive,

        @Schema(description = "Measurement unit of the attribute", examples = {"milliseconds"}, enumeration = {"milliseconds", "bytes", "seconds", "per_second", "nanoseconds", "minutes", "percentage", "days"})
        String unit,

        @Schema(description = "Name of the referenced capability (null if none)")
        String capabilityReference) implements Properties {
}
