package org.wildfly.modelgraph.api.operation;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Full operation detail including parameters sorted by name and an optional return value. */
@RegisterForReflection
@Schema(description = "Detailed operation information including parameters and return value")
public record OperationDetailResponse(
        @Schema(description = "Operation name", examples = {"add"})
        String name,
        @Schema(description = "Operation description")
        String description,
        @Schema(description = "Stability level", examples = {"stable"})
        String stability,
        @Schema(description = "Whether the operation is read-only")
        boolean readOnly,
        @Schema(description = "Whether the operation is runtime-only")
        boolean runtimeOnly,
        @Schema(description = "Whether the operation is global")
        boolean global,
        @Schema(description = "Deprecation info, null if not deprecated")
        Deprecation deprecation,
        @Schema(description = "Owning resource name", examples = {"data-source"})
        String resourceName,
        @Schema(description = "Owning resource address", examples = {"/subsystem=datasources/data-source=*"})
        String resourceAddress,
        @Schema(description = "Operation parameters, sorted by name")
        List<Parameter> parameters,
        @Schema(description = "Return value, null if operation has no return value")
        ReturnValue returnValue) {

    @RegisterForReflection
    @Schema(description = "Deprecation information")
    public record Deprecation(
            @Schema(description = "Version since which the operation is deprecated", examples = {"29.0.0"})
            String since,
            @Schema(description = "Reason for deprecation")
            String reason) {
    }

    @RegisterForReflection
    @Schema(description = "Operation parameter")
    public record Parameter(
            @Schema(description = "Parameter name", examples = {"connection-url"})
            String name,
            @Schema(description = "Parameter data type", examples = {"STRING"})
            String type,
            @Schema(description = "Parameter description")
            String description,
            @Schema(description = "Whether the parameter is required")
            boolean required,
            @Schema(description = "Whether the parameter is nillable")
            boolean nillable,
            @Schema(description = "Default value")
            String defaultValue,
            @Schema(description = "List of allowed values, null if unrestricted")
            List<String> allowedValues) {
    }

    @RegisterForReflection
    @Schema(description = "Operation return value")
    public record ReturnValue(
            @Schema(description = "Return value type", examples = {"STRING"})
            String type,
            @Schema(description = "Return value description")
            String description) {
    }
}
