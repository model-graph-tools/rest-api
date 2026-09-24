package org.wildfly.modelgraph.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResult(String type, String name, List<ResourceRef> providedBy, String description,
        String address) {
}
