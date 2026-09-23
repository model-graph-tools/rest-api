package org.wildfly.modelgraph.api;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record CapabilityReference(String attributeName, String resourceName, String resourceAddress) {
}
