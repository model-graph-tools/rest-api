package org.wildfly.modelgraph.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A single search result representing a resource, attribute, operation, or capability")
public record SearchResult(
        @Schema(description = "Result type", example = "resource", enumeration = {"resource", "attribute", "operation", "capability"})
        String type,
        @Schema(description = "Element name", example = "data-source")
        String name,
        @Schema(description = "Resources that provide this capability (capabilities only)")
        List<ResourceRef> providedBy,
        @Schema(description = "Element description")
        String description,
        @Schema(description = "Resource address (null for some capabilities)", example = "/subsystem=datasources/data-source=*")
        String address,
        @Schema(description = "Stability level", example = "stable")
        String stability,
        @Schema(description = "Whether the resource is a singleton (resources only)")
        Boolean singleton) {
}
