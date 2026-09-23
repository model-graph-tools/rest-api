package org.wildfly.modelgraph.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;

@ApplicationScoped
public class ModelGraphRepository {

    private static final String SEARCH_QUERY = """
            CALL {
                MATCH (r:Resource)
                WHERE (r.name CONTAINS $term OR r.description CONTAINS $term)
                  AND NOT r.address STARTS WITH '/deployment'
                RETURN 'Resource' AS type, r.name AS name, r.description AS description,
                       r.address AS address, null AS attributeName
                ORDER BY CASE WHEN r.name CONTAINS $term THEN 0 ELSE 1 END, r.name
                LIMIT $limit
            }
            RETURN type, name, description, address, attributeName
            UNION ALL
            CALL {
                MATCH (a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource)
                WHERE (a.name CONTAINS $term OR a.description CONTAINS $term)
                  AND NOT r.address STARTS WITH '/deployment'
                RETURN 'Attribute' AS type, a.name AS name, a.description AS description,
                       r.address AS address, a.name AS attributeName
                ORDER BY CASE WHEN a.name CONTAINS $term THEN 0 ELSE 1 END, a.name
                LIMIT $limit
            }
            RETURN type, name, description, address, attributeName
            UNION ALL
            CALL {
                MATCH (c:Capability)
                WHERE c.name CONTAINS $term
                OPTIONAL MATCH (c)<-[:DECLARES_CAPABILITY]-(r:Resource)
                WHERE NOT r.address STARTS WITH '/deployment'
                RETURN 'Capability' AS type, c.name AS name, null AS description,
                       r.address AS address, null AS attributeName
                ORDER BY c.name
                LIMIT $limit
            }
            RETURN type, name, description, address, attributeName
            """;

    private static final String CAPABILITY_REFERENCES_QUERY = """
            MATCH (c:Capability {name: $name})<-[:REFERENCES_CAPABILITY]-(a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource)
            WHERE NOT r.address STARTS WITH '/deployment'
            RETURN a.name AS attributeName, r.name AS resourceName, r.address AS resourceAddress
            ORDER BY r.address
            """;

    private static final String IDENTITY_QUERY = """
            MATCH (i:Identity)
            RETURN i.identifier AS identifier, i.`group-id` AS groupId, i.`artifact-id` AS artifactId,
                   i.name AS name, i.version AS version, i.type AS type,
                   i.description AS description, i.url AS url, i.`scm-url` AS scmUrl,
                   i.licenses AS licenses
            LIMIT 1
            """;

    @Inject
    Driver driver;

    public List<SearchResult> search(String term, int limit) {
        try (var session = driver.session()) {
            return session.run(SEARCH_QUERY, Map.of("term", term, "limit", limit))
                    .list(ModelGraphRepository::toSearchResult);
        }
    }

    public List<CapabilityReference> capabilityReferences(String name) {
        try (var session = driver.session()) {
            return session.run(CAPABILITY_REFERENCES_QUERY, Map.of("name", name))
                    .list(ModelGraphRepository::toCapabilityReference);
        }
    }

    public IdentityResponse identity() {
        try (var session = driver.session()) {
            var result = session.run(IDENTITY_QUERY);
            if (result.hasNext()) {
                var record = result.next();
                return new IdentityResponse(
                        record.get("identifier").asString(null),
                        record.get("groupId").asString(null),
                        record.get("artifactId").asString(null),
                        record.get("name").asString(null),
                        record.get("version").asString(null),
                        record.get("type").asString(null),
                        record.get("description").asString(null),
                        record.get("url").asString(null),
                        record.get("scmUrl").asString(null),
                        record.get("licenses").asString(null));
            }
            return new IdentityResponse(null, null, null, null, null, null, null, null, null, null);
        }
    }

    private static SearchResult toSearchResult(Record record) {
        return new SearchResult(
                record.get("type").asString(),
                record.get("name").asString(null),
                record.get("description").asString(null),
                record.get("address").asString(null),
                record.get("attributeName").asString(null));
    }

    private static CapabilityReference toCapabilityReference(Record record) {
        return new CapabilityReference(
                record.get("attributeName").asString(null),
                record.get("resourceName").asString(null),
                record.get("resourceAddress").asString(null));
    }

}
