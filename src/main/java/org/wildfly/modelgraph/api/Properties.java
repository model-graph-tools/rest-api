package org.wildfly.modelgraph.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import io.quarkus.runtime.annotations.RegisterForReflection;

@JsonInclude(JsonInclude.Include.NON_NULL)
@RegisterForReflection
@Schema(description = "Type-specific properties for catalog results")
public sealed interface Properties permits ResourceProperties, AttributeProperties, OperationProperties {
}
