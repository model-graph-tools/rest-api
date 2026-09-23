package org.wildfly.modelgraph.api;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record IdentityResponse(
        String identifier,
        String groupId,
        String artifactId,
        String name,
        String version,
        String type,
        String description,
        String url,
        String scmUrl,
        String licenses) {
}
