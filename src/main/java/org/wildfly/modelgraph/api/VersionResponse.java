package org.wildfly.modelgraph.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VersionResponse(String version, String buildTime, String commit, String tag) {
}
