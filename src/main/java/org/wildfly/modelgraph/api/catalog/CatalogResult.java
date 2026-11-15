package org.wildfly.modelgraph.api.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** A single catalog hit with type-dependent properties; the {@code properties} field is polymorphic based on the result type. */
@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A single catalog result representing a resource, attribute, operation, or capability")
public record CatalogResult(
        @Schema(description = "Node type", examples = {"attribute"}, enumeration = {"resource", "attribute", "operation", "capability"})
        String type,

        @Schema(description = "Element name", examples = {"connection-url"})
        String name,

        @Schema(description = "Element description (may be null)", examples = {"The JDBC driver connection URL"})
        String description,

        @Schema(description = "Full address. For attributes: includes attribute name segment. Null for some capabilities.", examples = {"/subsystem=datasources/data-source=*/connection-url"})
        String address,

        @Schema(description = "Parent resource address without the attribute/operation name. Present for attributes and operations.", examples = {"/subsystem=datasources/data-source=*"})
        String resourceAddress,

        @Schema(description = "Stability level", examples = {"default"}, enumeration = {"default", "community", "preview", "experimental"})
        String stability,

        @Schema(description = "Whether the element is deprecated")
        boolean deprecated,

        @Schema(description = "Type-specific properties. Present for resources, attributes, and operations. Null for capabilities.")
        Properties properties) {
}
