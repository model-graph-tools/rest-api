package org.wildfly.modelgraph.api;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@RegisterForReflection
@Schema(description = "Detailed resource information including child resources, attributes, operations, and capabilities")
public record ResourceDetailResponse(
        @Schema(description = "Resource name", example = "buffer-pool")
        String name,
        @Schema(description = "Full resource address", example = "/subsystem=io/buffer-pool=*")
        String address,
        @Schema(description = "Resource description")
        String description,
        @Schema(description = "Stability level", example = "stable", enumeration = {"default", "community", "preview", "experimental"})
        String stability,
        @Schema(description = "Whether the resource is a singleton")
        boolean singleton,
        @Schema(description = "Deprecation info, null if not deprecated")
        Deprecation deprecation,
        @Schema(description = "Parent resource address, null for root resources", example = "/subsystem=io")
        String parentAddress,
        @Schema(description = "Direct child resources, sorted by name")
        List<ChildResource> childResources,
        @Schema(description = "Attributes of this resource, sorted by name")
        List<AttributeSummary> attributes,
        @Schema(description = "Operations provided by this resource, sorted by name")
        List<OperationSummary> operations,
        @Schema(description = "Capabilities declared by this resource, sorted by name")
        List<CapabilitySummary> capabilities) {

    @RegisterForReflection
    @Schema(description = "Deprecation information")
    public record Deprecation(
            @Schema(description = "Version since which the element is deprecated", example = "29.0.0")
            String since,
            @Schema(description = "Reason for deprecation")
            String reason) {
    }

    @RegisterForReflection
    @Schema(description = "A child resource reference")
    public record ChildResource(
            @Schema(description = "Child resource name", example = "worker")
            String name,
            @Schema(description = "Child resource address", example = "/subsystem=io/worker=*")
            String address) {
    }

    @RegisterForReflection
    @Schema(description = "Attribute summary for the resource detail view")
    public record AttributeSummary(
            @Schema(description = "Attribute name", example = "buffer-size")
            String name,
            @Schema(description = "Data type", example = "INT")
            String type,
            @Schema(description = "Stability level", example = "stable")
            String stability,
            @Schema(description = "Access type", example = "read-write", enumeration = {"read-write", "read-only", "metric"})
            String accessType,
            @Schema(description = "Storage type", example = "configuration", enumeration = {"configuration", "runtime"})
            String storage,
            @Schema(description = "Whether the attribute is deprecated")
            boolean deprecated) {
    }

    @RegisterForReflection
    @Schema(description = "Operation summary for the resource detail view")
    public record OperationSummary(
            @Schema(description = "Operation name", example = "add")
            String name,
            @Schema(description = "Stability level", example = "stable")
            String stability,
            @Schema(description = "Whether the operation is read-only")
            boolean readOnly,
            @Schema(description = "Whether the operation is runtime-only")
            boolean runtimeOnly,
            @Schema(description = "Whether the operation is global")
            boolean global,
            @Schema(description = "Whether the operation is deprecated")
            boolean deprecated) {
    }

    @RegisterForReflection
    @Schema(description = "Capability declared by this resource")
    public record CapabilitySummary(
            @Schema(description = "Capability name", example = "org.wildfly.io.buffer-pool")
            String name) {
    }
}
