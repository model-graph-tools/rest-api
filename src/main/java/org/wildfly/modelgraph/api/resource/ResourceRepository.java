package org.wildfly.modelgraph.api.resource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Value;

/** Queries the Neo4j graph for detailed resource information including children, attributes, operations, and capabilities. */
@ApplicationScoped
public class ResourceRepository {

    private static final String RESOURCE_DETAIL_QUERY = """
            MATCH (r:Resource {address: $address})
            OPTIONAL MATCH (r)-[:CHILD_OF]->(parent:Resource)
            OPTIONAL MATCH (r)-[rdep:DEPRECATED_SINCE]->(dv:Version)
            WITH r, parent.address AS parentAddress,
                 CASE WHEN dv IS NOT NULL THEN {since: dv.major + '.' + dv.minor + '.' + dv.ordinal, reason: rdep.reason} END AS deprecation
            OPTIONAL MATCH (child:Resource)-[:CHILD_OF]->(r)
            WITH r, parentAddress, deprecation, child
            ORDER BY child.name
            WITH r, parentAddress, deprecation,
                 COLLECT(CASE WHEN child IS NOT NULL THEN {name: child.name, address: child.address} END) AS children
            OPTIONAL MATCH (r)-[:HAS_ATTRIBUTE]->(a:Attribute)
            OPTIONAL MATCH (a)-[adep:DEPRECATED_SINCE]->()
            WITH r, parentAddress, deprecation, children, a, adep
            ORDER BY a.name
            WITH r, parentAddress, deprecation, children,
                 COLLECT(CASE WHEN a IS NOT NULL THEN {
                     name: a.name, type: a.type, stability: a.stability,
                     accessType: a.`access-type`, storage: a.storage,
                     deprecated: adep IS NOT NULL
                 } END) AS attributes
            OPTIONAL MATCH (r)-[:PROVIDES]->(o:Operation)
            OPTIONAL MATCH (o)-[odep:DEPRECATED_SINCE]->()
            WITH r, parentAddress, deprecation, children, attributes, o, odep
            ORDER BY o.name
            With r, parentAddress, deprecation, children, attributes,
                 COLLECT(CASE WHEN o IS NOT NULL THEN {
                     name: o.name, stability: o.stability,
                     readOnly: o.`read-only`, runtimeOnly: o.`runtime-only`, global: o.global,
                     deprecated: odep IS NOT NULL
                 } END) AS operations
            OPTIONAL MATCH (r)-[:DECLARES_CAPABILITY]->(c:Capability)
            With r, parentAddress, deprecation, children, attributes, operations, c
            ORDER BY c.name
            With r, parentAddress, deprecation, children, attributes, operations,
                 COLLECT(CASE WHEN c IS NOT NULL THEN {name: c.name} END) AS capabilities
            RETURN r.name AS name, r.address AS address, r.description AS description,
                   r.stability AS stability, r.singleton AS singleton,
                   parentAddress, deprecation, children, attributes, operations, capabilities
            """;

    @Inject
    Driver driver;

    public Optional<ResourceDetailResponse> resourceDetail(String address) {
        try (var session = driver.session()) {
            var result = session.run(RESOURCE_DETAIL_QUERY, Map.of("address", address));
            if (result.hasNext()) {
                var record = result.next();
                ResourceDetailResponse.Deprecation deprecation = null;
                Value depValue = record.get("deprecation");
                if (!depValue.isNull()) {
                    deprecation = new ResourceDetailResponse.Deprecation(
                            depValue.get("since").asString(null),
                            depValue.get("reason").asString(null));
                }
                List<ResourceDetailResponse.ChildResource> children = record.get("children")
                        .asList(v -> new ResourceDetailResponse.ChildResource(
                                v.get("name").asString(null),
                                v.get("address").asString(null)));
                List<ResourceDetailResponse.AttributeSummary> attributes = record.get("attributes")
                        .asList(v -> new ResourceDetailResponse.AttributeSummary(
                                v.get("name").asString(null),
                                v.get("type").asString(null),
                                v.get("stability").asString(null),
                                v.get("accessType").asString(null),
                                v.get("storage").asString(null),
                                v.get("deprecated").asBoolean(false)));
                List<ResourceDetailResponse.OperationSummary> operations = record.get("operations")
                        .asList(v -> new ResourceDetailResponse.OperationSummary(
                                v.get("name").asString(null),
                                v.get("stability").asString(null),
                                v.get("readOnly").asBoolean(false),
                                v.get("runtimeOnly").asBoolean(false),
                                v.get("global").asBoolean(false),
                                v.get("deprecated").asBoolean(false)));
                List<ResourceDetailResponse.CapabilitySummary> capabilities = record.get("capabilities")
                        .asList(v -> new ResourceDetailResponse.CapabilitySummary(
                                v.get("name").asString(null)));
                return Optional.of(new ResourceDetailResponse(
                        record.get("name").asString(null),
                        record.get("address").asString(null),
                        record.get("description").asString(null),
                        record.get("stability").asString(null),
                        record.get("singleton").asBoolean(false),
                        deprecation,
                        record.get("parentAddress").asString(null),
                        children, attributes, operations, capabilities));
            }
            return Optional.empty();
        }
    }
}
