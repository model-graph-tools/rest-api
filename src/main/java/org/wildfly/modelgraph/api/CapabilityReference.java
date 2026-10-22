package org.wildfly.modelgraph.api;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record CapabilityReference(String attributeName, String attributeStability, String resourceName,
        String resourceAddress, String resourceStability) {
}
