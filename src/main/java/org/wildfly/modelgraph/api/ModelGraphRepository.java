package org.wildfly.modelgraph.api;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.ClientException;

@ApplicationScoped
public class ModelGraphRepository {

    static final String SEARCH_INDEX = "mgt_search";

    private static final String SEARCH_QUERY = """
            CALL {
                CALL db.index.fulltext.queryNodes('%s', $term) YIELD node, score
                WHERE node:Resource
                  AND NOT node.address STARTS WITH '/deployment'
                RETURN 'Resource' AS type, node.name AS name, node.description AS description,
                       node.address AS address, null AS providedBy, score
                ORDER BY score DESC, node.name
                LIMIT $limit
            }
            RETURN type, name, description, address, providedBy, score
            UNION ALL
            CALL {
                CALL db.index.fulltext.queryNodes('%s', $term) YIELD node, score
                WHERE node:Attribute
                MATCH (node)<-[:HAS_ATTRIBUTE]-(r:Resource)
                WHERE NOT r.address STARTS WITH '/deployment'
                RETURN 'Attribute' AS type, node.name AS name, node.description AS description,
                       r.address AS address, null AS providedBy, score
                ORDER BY score DESC, node.name
                LIMIT $limit
            }
            RETURN type, name, description, address, providedBy, score
            UNION ALL
            CALL {
                CALL db.index.fulltext.queryNodes('%s', $term) YIELD node, score
                WHERE node:Capability
                OPTIONAL MATCH (node)<-[:DECLARES_CAPABILITY]-(r:Resource)
                WHERE NOT r.address STARTS WITH '/deployment'
                WITH node, score,
                     COLLECT(CASE WHEN r IS NOT NULL THEN {name: r.name, address: r.address} END) AS providedBy
                RETURN 'Capability' AS type, node.name AS name, null AS description,
                       null AS address, providedBy, score
                ORDER BY score DESC, node.name
                LIMIT $limit
            }
            RETURN type, name, description, address, providedBy, score
            UNION ALL
            CALL {
                CALL db.index.fulltext.queryNodes('%s', $term) YIELD node, score
                WHERE node:Operation AND NOT node.global
                MATCH (node)<-[:PROVIDES]-(r:Resource)
                WHERE NOT r.address STARTS WITH '/deployment'
                RETURN 'Operation' AS type, node.name AS name, node.description AS description,
                       r.address AS address, null AS providedBy, score
                ORDER BY score DESC, node.name
                LIMIT $limit
            }
            RETURN type, name, description, address, providedBy, score
            """.formatted(SEARCH_INDEX, SEARCH_INDEX, SEARCH_INDEX, SEARCH_INDEX);

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

    private static final Logger LOG = Logger.getLogger(ModelGraphRepository.class);

    @Inject
    Driver driver;

    public List<SearchResult> search(String term, int limit) {
        String luceneTerm = escapeAndWildcard(term);
        if (luceneTerm.isEmpty()) {
            return List.of();
        }
        try (var session = driver.session()) {
            return session.run(SEARCH_QUERY, Map.of("term", luceneTerm, "limit", limit))
                    .list(ModelGraphRepository::toSearchResult);
        } catch (ClientException e) {
            LOG.warnf("Invalid search query '%s' (escaped: '%s'): %s", term, luceneTerm, e.getMessage());
            return List.of();
        }
    }

    static String escapeAndWildcard(String term) {
        // Strip leading/trailing delimiters (including dots — a leading dot is noise).
        // But do NOT split on dots — Neo4j's standard analyzer keeps them as part of tokens
        // (e.g., "org.wildfly.io.buffer" is one token). Only split on hyphens, underscores, and spaces.
        String stripped = term.replaceAll("^[\\s\\-._]+|[\\s\\-._]+$", "");
        if (stripped.isEmpty()) {
            return "";
        }
        String[] parts = stripped.split("[\\s\\-_]+");
        if (parts.length <= 1) {
            String escaped = escapeLucene(stripped);
            return escaped + "*";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(" AND ");
            }
            String escaped = escapeLucene(parts[i]);
            if (i == parts.length - 1) {
                sb.append(escaped).append("*");
            } else {
                sb.append(escaped);
            }
        }
        return sb.toString();
    }

    private static String escapeLucene(String s) {
        String escaped = s.replaceAll("([+&|!(){}\\[\\]^\"~*?:\\\\/])", "\\\\$1");
        return java.util.regex.Pattern.compile("\\b(AND|OR|NOT)\\b")
                .matcher(escaped)
                .replaceAll(m -> m.group().toLowerCase());
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
        Value providedByValue = record.get("providedBy");
        List<ResourceRef> providedBy = null;
        if (!providedByValue.isNull() && !providedByValue.isEmpty()) {
            providedBy = providedByValue.asList(v ->
                    new ResourceRef(v.get("name").asString(null), v.get("address").asString(null)));
        }
        return new SearchResult(
                record.get("type").asString(),
                record.get("name").asString(null),
                providedBy,
                record.get("description").asString(null),
                record.get("address").asString(null));
    }

    private static CapabilityReference toCapabilityReference(Record record) {
        return new CapabilityReference(
                record.get("attributeName").asString(null),
                record.get("resourceName").asString(null),
                record.get("resourceAddress").asString(null));
    }

}
