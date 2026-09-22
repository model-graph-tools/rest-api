package org.wildfly.modelgraph.api;

import java.util.List;

public record CapabilityReferencesResponse(String capability, List<CapabilityReference> references) {
}
