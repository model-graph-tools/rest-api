package org.wildfly.modelgraph.api;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record VersionResponse(String version, String sourceType) {
}
