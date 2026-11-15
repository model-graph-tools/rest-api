package org.wildfly.modelgraph.api.resource;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Full resource detail including child resources, attributes, operations, and declared capabilities, all sorted by name. */
@RegisterForReflection
@Schema(description = "Detailed resource information including child resources, attributes, operations, and capabilities")
public record ResourceDetailResponse(
        @Schema(description = "Resource name", examples = {"buffer-pool"})
        String name,
        @Schema(description = "Full resource address", examples = {"/subsystem=io/buffer-pool=*"})
        String address,
        @Schema(description = "Resource description")
        String description,
        @Schema(description = "Stability level", examples = {"stable"}, enumeration = {"default", "community", "preview", "experimental"})
        String stability,
        @Schema(description = "Whether the resource is a singleton")
        boolean singleton,
        @Schema(description = "Deprecation info, null if not deprecated")
        Deprecation deprecation,
        @Schema(description = "Parent resource address, null for root resources", examples = {"/subsystem=io"})
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
            @Schema(description = "Version since which the element is deprecated", examples = {"29.0.0"})
            String since,
            @Schema(description = "Reason for deprecation")
            String reason) {
    }

    @RegisterForReflection
    @Schema(description = "A child resource reference")
    public record ChildResource(
            @Schema(description = "Child resource name", examples = {"worker"})
            String name,
            @Schema(description = "Child resource address", examples = {"/subsystem=io/worker=*"})
            String address) {
    }

    @RegisterForReflection
    @Schema(description = "Attribute summary for the resource detail view")
    public record AttributeSummary(
            @Schema(description = "Attribute name", examples = {"buffer-size"})
            String name,
            @Schema(description = "Data type", examples = {"INT"})
            String type,
            @Schema(description = "Stability level", examples = {"stable"})
            String stability,
            @Schema(description = "Access type", examples = {"read-write"}, enumeration = {"read-write", "read-only", "metric"})
            String accessType,
            @Schema(description = "Storage type", examples = {"configuration"}, enumeration = {"configuration", "runtime"})
            String storage,
            @Schema(description = "Whether the attribute is deprecated")
            boolean deprecated) {
    }

    @RegisterForReflection
    @Schema(description = "Operation summary for the resource detail view")
    public record OperationSummary(
            @Schema(description = "Operation name", examples = {"add"})
            String name,
            @Schema(description = "Stability level", examples = {"stable"})
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
            @Schema(description = "Capability name", examples = {"org.wildfly.io.buffer-pool"})
            String name) {
    }
}
