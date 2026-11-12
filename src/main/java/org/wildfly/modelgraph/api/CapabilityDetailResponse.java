package org.wildfly.modelgraph.api;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record CapabilityDetailResponse(String name, String stability,
        List<DeclaredBy> declaredBy, List<CapabilityReference> referencedBy) {

    @RegisterForReflection
    public record DeclaredBy(String resourceName, String resourceAddress) {
    }
}
