package org.wildfly.modelgraph.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResult(String type, String name, String description, String address, String attributeName) {
}
