package org.wildfly.modelgraph.api.search;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A single search hit with type-dependent fields; nullable fields vary by result type. */
@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A single search result representing a resource, attribute, operation, or capability")
public record SearchResult(
        @Schema(description = "Result type", examples = {"resource"}, enumeration = {"resource", "attribute", "operation", "capability"})
        String type,
        @Schema(description = "Element name", examples = {"data-source"})
        String name,
        @Schema(description = "Resources that provide this capability (capabilities only)")
        List<ResourceRef> providedBy,
        @Schema(description = "Element description")
        String description,
        @Schema(description = "Resource address (null for some capabilities)", examples = {"/subsystem=datasources/data-source=*"})
        String address,
        @Schema(description = "Stability level", examples = {"stable"})
        String stability,
        @Schema(description = "Whether the resource is a singleton (resources only)")
        Boolean singleton) {
}
