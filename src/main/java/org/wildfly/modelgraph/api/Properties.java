package org.wildfly.modelgraph.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.quarkus.runtime.annotations.RegisterForReflection;

@JsonInclude(JsonInclude.Include.NON_NULL)
@RegisterForReflection
public sealed interface Properties permits ResourceProperties, AttributeProperties, OperationProperties {
}
