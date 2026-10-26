package org.wildfly.modelgraph.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A single catalog result representing a resource, attribute, operation, or capability")
public record CatalogResult(
        @Schema(description = "Node type", example = "attribute", enumeration = {"resource", "attribute", "operation", "capability"})
        String type,

        @Schema(description = "Element name", example = "connection-url")
        String name,

        @Schema(description = "Element description (may be null)", example = "The JDBC driver connection URL")
        String description,

        @Schema(description = "Full address. For attributes: includes attribute name segment. Null for some capabilities.", example = "/subsystem=datasources/data-source=*/connection-url")
        String address,

        @Schema(description = "Parent resource address without the attribute/operation name. Present for attributes and operations.", example = "/subsystem=datasources/data-source=*")
        String resourceAddress,

        @Schema(description = "Stability level", example = "default", enumeration = {"default", "community", "preview", "experimental"})
        String stability,

        @Schema(description = "Whether the element is deprecated")
        boolean deprecated,

        @Schema(description = "Type-specific properties. Present for resources, attributes, and operations. Null for capabilities.")
        Properties properties) {
}
