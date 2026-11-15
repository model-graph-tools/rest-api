package org.wildfly.modelgraph.api.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** Sealed interface for type-specific catalog result properties, discriminated by the result's node type. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@RegisterForReflection
@Schema(description = "Type-specific properties for catalog results")
public sealed interface Properties permits ResourceProperties, AttributeProperties, OperationProperties {
}
