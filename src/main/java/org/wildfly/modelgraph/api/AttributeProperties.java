package org.wildfly.modelgraph.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttributeProperties(
        String dataType,
        String accessType,
        String storage,
        String restart,
        boolean required,
        boolean nillable,
        boolean expressions,
        boolean sensitive,
        String unit) implements Properties {
}
