package org.wildfly.modelgraph.api.capability;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;

/** Queries the Neo4j graph for capability details and the attributes that reference a capability. */
@ApplicationScoped
public class CapabilityRepository {

    private static final String CAPABILITY_REFERENCES_QUERY = """
            MATCH (c:Capability {name: $name})<-[:REFERENCES_CAPABILITY]-(a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource)
            RETURN a.name AS attributeName, a.stability AS attributeStability,
                   r.name AS resourceName, r.address AS resourceAddress, r.stability AS resourceStability
            ORDER BY r.address
            """;

    private static final String CAPABILITY_DETAIL_QUERY = """
            MATCH (c:Capability {name: $name})
            OPTIONAL MATCH (c)<-[d:DECLARES_CAPABILITY]-(r:Resource)
            WITH c, r, d
            ORDER BY r.name
            WITH c, COLLECT(CASE WHEN r IS NOT NULL THEN {resourceName: r.name, resourceAddress: r.address, stability: d.stability} END) AS declaredBy
            OPTIONAL MATCH (c)<-[:REFERENCES_CAPABILITY]-(a:Attribute)<-[:HAS_ATTRIBUTE]-(res:Resource)
            WITH c, declaredBy, a, res
            ORDER BY a.name
            With c, declaredBy,
                 COLLECT(CASE WHEN a IS NOT NULL THEN {
                     attributeName: a.name, attributeStability: a.stability,
                     resourceName: res.name, resourceAddress: res.address, resourceStability: res.stability
                 } END) AS referencedBy
            RETURN c.name AS name, declaredBy, referencedBy
            """;

    @Inject
    Driver driver;

    public List<CapabilityReference> capabilityReferences(String name) {
        try (var session = driver.session()) {
            return session.run(CAPABILITY_REFERENCES_QUERY, Map.of("name", name))
                    .list(CapabilityRepository::toCapabilityReference);
        }
    }

    public Optional<CapabilityDetailResponse> capabilityDetail(String name) {
        try (var session = driver.session()) {
            var result = session.run(CAPABILITY_DETAIL_QUERY, Map.of("name", name));
            if (result.hasNext()) {
                var record = result.next();
                List<CapabilityDetailResponse.DeclaredBy> declaredBy = record.get("declaredBy")
                        .asList(v -> new CapabilityDetailResponse.DeclaredBy(
                                v.get("resourceName").asString(null),
                                v.get("resourceAddress").asString(null),
                                v.get("stability").asString(null)));
                List<CapabilityReference> referencedBy = record.get("referencedBy")
                        .asList(v -> new CapabilityReference(
                                v.get("attributeName").asString(null),
                                v.get("attributeStability").asString(null),
                                v.get("resourceName").asString(null),
                                v.get("resourceAddress").asString(null),
                                v.get("resourceStability").asString(null)));
                return Optional.of(new CapabilityDetailResponse(
                        record.get("name").asString(null),
                        declaredBy,
                        referencedBy));
            }
            return Optional.empty();
        }
    }

    private static CapabilityReference toCapabilityReference(Record record) {
        return new CapabilityReference(
                record.get("attributeName").asString(null),
                record.get("attributeStability").asString(null),
                record.get("resourceName").asString(null),
                record.get("resourceAddress").asString(null),
                record.get("resourceStability").asString(null));
    }
}
