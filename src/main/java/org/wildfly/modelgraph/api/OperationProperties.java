package org.wildfly.modelgraph.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OperationProperties(
        boolean readOnly,
        boolean runtimeOnly,
        boolean global,
        boolean hasParams,
        boolean hasReturnValue) implements Properties {
}
