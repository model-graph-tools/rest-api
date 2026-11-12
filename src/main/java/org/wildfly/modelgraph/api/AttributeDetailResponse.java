package org.wildfly.modelgraph.api;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@RegisterForReflection
@Schema(description = "Detailed attribute information including relationships and metadata")
public record AttributeDetailResponse(
        @Schema(description = "Attribute name", example = "connection-url")
        String name,
        @Schema(description = "Attribute description")
        String description,
        @Schema(description = "Data type", example = "STRING")
        String type,
        @Schema(description = "Stability level", example = "stable")
        String stability,
        @Schema(description = "Whether the attribute is required")
        boolean required,
        @Schema(description = "Whether the attribute is nillable")
        boolean nillable,
        @Schema(description = "Whether expressions are allowed")
        boolean expressionsAllowed,
        @Schema(description = "Access type", example = "read-write", enumeration = {"read-write", "read-only", "metric"})
        String accessType,
        @Schema(description = "Storage type", example = "configuration", enumeration = {"configuration", "runtime"})
        String storage,
        @Schema(description = "Restart policy", example = "no-services", enumeration = {"all-services", "no-services", "resource-services", "jvm"})
        String restartRequired,
        @Schema(description = "Attribute group name")
        String attributeGroup,
        @Schema(description = "Measurement unit", example = "BYTES")
        String unit,
        @Schema(description = "Default value")
        String defaultValue,
        @Schema(description = "List of allowed values, null if unrestricted")
        List<String> allowedValues,
        @Schema(description = "Deprecation info, null if not deprecated")
        Deprecation deprecation,
        @Schema(description = "Whether the attribute is sensitive")
        boolean sensitive,
        @Schema(description = "Owning resource name", example = "data-source")
        String resourceName,
        @Schema(description = "Owning resource address", example = "/subsystem=datasources/data-source=*")
        String resourceAddress,
        @Schema(description = "Name of the capability this attribute references, null if none")
        String referencesCapability,
        @Schema(description = "Required sibling attributes")
        List<AttributeRef> requires,
        @Schema(description = "Alternative sibling attributes")
        List<AttributeRef> alternatives,
        @Schema(description = "Sub-attributes for complex (OBJECT/LIST) types")
        List<SubAttribute> subAttributes) {

    @RegisterForReflection
    @Schema(description = "Deprecation information")
    public record Deprecation(
            @Schema(description = "Version since which the attribute is deprecated", example = "29.0.0")
            String since,
            @Schema(description = "Reason for deprecation")
            String reason) {
    }

    @RegisterForReflection
    @Schema(description = "Reference to a sibling attribute")
    public record AttributeRef(
            @Schema(description = "Attribute name", example = "pool-size")
            String name) {
    }

    @RegisterForReflection
    @Schema(description = "Sub-attribute of a complex attribute")
    public record SubAttribute(
            @Schema(description = "Sub-attribute name", example = "max-threads")
            String name,
            @Schema(description = "Sub-attribute data type", example = "INT")
            String type) {
    }
}
