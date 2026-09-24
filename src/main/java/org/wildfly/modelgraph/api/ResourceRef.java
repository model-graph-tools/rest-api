package org.wildfly.modelgraph.api;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record ResourceRef(String name, String address) {
}
