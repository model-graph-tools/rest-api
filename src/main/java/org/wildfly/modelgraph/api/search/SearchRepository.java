package org.wildfly.modelgraph.api.search;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.ClientException;
import org.wildfly.modelgraph.api.LuceneUtils;

/** Queries the Neo4j full-text index ({@code mgt_search}) using a UNION ALL strategy across resource, attribute, capability, and operation branches. */
@ApplicationScoped
public class SearchRepository {

    private static final String SEARCH_QUERY = """
            CALL {
                CALL db.index.fulltext.queryNodes('%s', $term) YIELD node, score
                WHERE node:Resource
                  AND NOT node.address STARTS WITH '/deployment='
                RETURN 'resource' AS type, node.name AS name, node.description AS description,
                       node.address AS address, null AS providedBy, node.stability AS stability,
                       node.singleton AS singleton, score
                ORDER BY score DESC, node.name
                LIMIT $limit
            }
            RETURN type, name, description, address, providedBy, stability, singleton, score
            UNION ALL
            CALL {
                CALL db.index.fulltext.queryNodes('%s', $term) YIELD node, score
                WHERE node:Attribute
                MATCH (node)<-[:HAS_ATTRIBUTE]-(r:Resource)
                WHERE NOT r.address STARTS WITH '/deployment='
                RETURN 'attribute' AS type, node.name AS name, node.description AS description,
                       r.address AS address, null AS providedBy, node.stability AS stability,
                       r.singleton AS singleton, score
                ORDER BY score DESC, node.name
                LIMIT $limit
            }
            RETURN type, name, description, address, providedBy, stability, singleton, score
            UNION ALL
            CALL {
                MATCH (node:Capability)
                WHERE ALL(part IN $capabilityTerms WHERE
                    toLower(replace(node.name, '-', '')) CONTAINS part)
                OPTIONAL MATCH (node)<-[:DECLARES_CAPABILITY]-(r:Resource)
                WHERE NOT r.address STARTS WITH '/deployment='
                WITH node,
                     COLLECT(CASE WHEN r IS NOT NULL THEN {name: r.name, address: r.address} END) AS providedBy
                RETURN 'capability' AS type, node.name AS name, null AS description,
                       null AS address, providedBy, node.stability AS stability,
                       null AS singleton, 1.0 AS score
                ORDER BY node.name
                LIMIT $limit
            }
            RETURN type, name, description, address, providedBy, stability, singleton, score
            UNION ALL
            CALL {
                CALL db.index.fulltext.queryNodes('%s', $term) YIELD node, score
                WHERE node:Operation AND NOT node.global
                MATCH (node)<-[:PROVIDES]-(r:Resource)
                WHERE NOT r.address STARTS WITH '/deployment='
                RETURN 'operation' AS type, node.name AS name, node.description AS description,
                       r.address AS address, null AS providedBy, node.stability AS stability,
                       r.singleton AS singleton, score
                ORDER BY score DESC, node.name
                LIMIT $limit
            }
            RETURN type, name, description, address, providedBy, stability, singleton, score
            """.formatted(LuceneUtils.SEARCH_INDEX, LuceneUtils.SEARCH_INDEX, LuceneUtils.SEARCH_INDEX);

    private static final Logger LOG = Logger.getLogger(SearchRepository.class);

    @Inject
    Driver driver;

    public List<SearchResult> search(String term, int limit) {
        String luceneTerm = LuceneUtils.escapeAndWildcard(term);
        if (luceneTerm.isEmpty()) {
            return List.of();
        }
        List<String> capTerms = LuceneUtils.capabilityTerms(term);
        try (var session = driver.session()) {
            return session.run(SEARCH_QUERY,
                            Map.of("term", luceneTerm, "limit", limit, "capabilityTerms", capTerms))
                    .list(SearchRepository::toSearchResult);
        } catch (ClientException e) {
            LOG.warnf("Invalid search query '%s' (escaped: '%s'): %s", term, luceneTerm, e.getMessage());
            return List.of();
        }
    }

    private static SearchResult toSearchResult(Record record) {
        Value providedByValue = record.get("providedBy");
        List<ResourceRef> providedBy = null;
        if (!providedByValue.isNull() && !providedByValue.isEmpty()) {
            providedBy = providedByValue.asList(v ->
                    new ResourceRef(v.get("name").asString(null), v.get("address").asString(null)));
        }
        Value singletonValue = record.get("singleton");
        Boolean singleton = singletonValue.isNull() ? null : singletonValue.asBoolean();
        return new SearchResult(
                record.get("type").asString(),
                record.get("name").asString(null),
                providedBy,
                record.get("description").asString(null),
                record.get("address").asString(null),
                record.get("stability").asString(null),
                singleton);
    }
}
