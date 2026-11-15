package org.wildfly.modelgraph.api.identity;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.neo4j.driver.Driver;

/** Queries the Neo4j graph for the WildFly feature pack identity metadata. */
@ApplicationScoped
public class IdentityRepository {

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
}
