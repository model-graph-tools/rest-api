package org.wildfly.modelgraph.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;

@ApplicationScoped
public class CatalogRepository {

    private static final Logger LOG = Logger.getLogger(CatalogRepository.class);
    private static final String SEARCH_INDEX = "mgt_search";

    @Inject
    Driver driver;

    // ------------------------------------------------------ search

    public List<CatalogResult> search(CatalogFilter filter) {
        String query = buildResultQuery(filter);
        Map<String, Object> params = buildParams(filter);
        LOG.debugf("Catalog search query: %s, params: %s", query, params);

        try (var session = driver.session()) {
            return session.run(query, params).list(CatalogRepository::toCatalogResult);
        }
    }

    public int total(CatalogFilter filter) {
        String query = buildTotalQuery(filter);
        Map<String, Object> params = buildParams(filter);
        LOG.debugf("Catalog total query: %s, params: %s", query, params);

        try (var session = driver.session()) {
            var result = session.run(query, params);
            if (result.hasNext()) {
                return result.next().get("total").asInt();
            }
            return 0;
        }
    }

    // ------------------------------------------------------ facets

    public Map<String, Map<String, Long>> facetCounts(CatalogFilter filter) {
        Map<String, Map<String, Long>> facets = new LinkedHashMap<>();
        facets.put("type", typeFacetCounts(filter));
        facets.put("stability", stabilityFacetCounts(filter));
        facets.put("deprecated", deprecatedFacetCounts(filter));
        return facets;
    }

    private Map<String, Long> typeFacetCounts(CatalogFilter filter) {
        // Type facet: apply all filters EXCEPT type
        StringBuilder query = new StringBuilder();
        query.append("CALL {\n");
        appendTypeFacetBranches(query, filter);
        query.append("}\n");
        query.append("RETURN type, count(*) AS count\n");

        Map<String, Object> params = buildFacetParams(filter);

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(query.toString(), params).list().forEach(record ->
                    counts.put(record.get("type").asString(), record.get("count").asLong()));
            return counts;
        }
    }

    private Map<String, Long> stabilityFacetCounts(CatalogFilter filter) {
        // Stability facet: apply all filters EXCEPT stability
        StringBuilder query = new StringBuilder();
        query.append("CALL {\n");
        appendStabilityFacetBranches(query, filter);
        query.append("}\n");
        query.append("RETURN stability, count(*) AS count\n");

        Map<String, Object> params = buildFacetParams(filter);

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(query.toString(), params).list().forEach(record -> {
                String stability = record.get("stability").asString(null);
                if (stability != null) {
                    counts.put(stability, record.get("count").asLong());
                }
            });
            return counts;
        }
    }

    private Map<String, Long> deprecatedFacetCounts(CatalogFilter filter) {
        // Deprecated facet: apply all filters EXCEPT deprecated
        StringBuilder query = new StringBuilder();
        query.append("CALL {\n");
        appendDeprecatedFacetBranches(query, filter);
        query.append("}\n");
        query.append("RETURN deprecated, count(*) AS count\n");

        Map<String, Object> params = buildFacetParams(filter);

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(query.toString(), params).list().forEach(record -> {
                boolean dep = record.get("deprecated").asBoolean(false);
                counts.merge(dep ? "yes" : "no", record.get("count").asLong(), Long::sum);
            });
            // Ensure both keys present
            counts.putIfAbsent("yes", 0L);
            counts.putIfAbsent("no", 0L);
            return counts;
        }
    }

    // ------------------------------------------------------ query builders

    String buildResultQuery(CatalogFilter filter) {
        StringBuilder query = new StringBuilder();
        query.append("CALL {\n");
        appendUnionBranches(query, filter);
        query.append("}\n");
        if (filter.hasSearchQuery()) {
            query.append("RETURN type, name, description, address, resourceAddress, stability, deprecated, score\n");
        } else {
            query.append("RETURN type, name, description, address, resourceAddress, stability, deprecated\n");
        }
        appendOrderBy(query, filter);
        query.append("SKIP $offset LIMIT $limit\n");
        return query.toString();
    }

    String buildTotalQuery(CatalogFilter filter) {
        StringBuilder query = new StringBuilder();
        query.append("CALL {\n");
        appendUnionBranches(query, filter);
        query.append("}\n");
        query.append("RETURN count(*) AS total\n");
        return query.toString();
    }

    // ------------------------------------------------------ order by

    private void appendOrderBy(StringBuilder query, CatalogFilter filter) {
        String sort = filter.sort();
        String dir = filter.order().toUpperCase();

        if ("score".equals(sort)) {
            query.append("ORDER BY score DESC, name ASC\n");
        } else if ("address".equals(sort)) {
            // Null addresses sort last regardless of direction
            query.append("ORDER BY CASE WHEN address IS NULL THEN 1 ELSE 0 END, address ")
                    .append(dir).append(", name ASC\n");
        } else {
            query.append("ORDER BY ").append(sort).append(" ").append(dir).append("\n");
        }
    }

    // ------------------------------------------------------ union branches

    private void appendUnionBranches(StringBuilder query, CatalogFilter filter) {
        List<String> branches = new ArrayList<>();

        if (filter.includesType(CatalogType.RESOURCE)) {
            branches.add(resourceBranch(filter));
        }
        if (filter.includesType(CatalogType.ATTRIBUTE)) {
            branches.add(attributeBranch(filter));
        }
        if (filter.includesType(CatalogType.OPERATION)) {
            branches.add(operationBranch(filter));
        }
        if (filter.includesType(CatalogType.CAPABILITY)) {
            branches.add(capabilityBranch(filter));
        }

        query.append(String.join("\nUNION ALL\n", branches));
        query.append("\n");
    }

    // ------------------------------------------------------ result branches

    private String resourceBranch(CatalogFilter filter) {
        StringBuilder sb = new StringBuilder();
        if (filter.hasSearchQuery()) {
            sb.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node, score\n");
            sb.append("WHERE node:Resource AND NOT node.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "node", filter);
            appendDeprecatedWhere(sb, "node", filter);
            appendScopeWhere(sb, "node", filter);
            sb.append("\nRETURN 'resource' AS type, node.name AS name, node.description AS description, ");
            sb.append("node.address AS address, null AS resourceAddress, toLower(node.stability) AS stability, ");
            sb.append("coalesce(node.deprecated, false) AS deprecated, score");
        } else {
            sb.append("MATCH (r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "r", filter);
            appendDeprecatedWhere(sb, "r", filter);
            appendScopeWhere(sb, "r", filter);
            sb.append("\nRETURN 'resource' AS type, r.name AS name, r.description AS description, ");
            sb.append("r.address AS address, null AS resourceAddress, toLower(r.stability) AS stability, ");
            sb.append("coalesce(r.deprecated, false) AS deprecated");
        }
        return sb.toString();
    }

    private String attributeBranch(CatalogFilter filter) {
        StringBuilder sb = new StringBuilder();
        if (filter.hasSearchQuery()) {
            sb.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node, score\n");
            sb.append("WHERE node:Attribute\n");
            sb.append("MATCH (node)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "node", filter);
            appendDeprecatedWhere(sb, "node", filter);
            appendScopeWhere(sb, "r", filter);
            sb.append("\nRETURN 'attribute' AS type, node.name AS name, node.description AS description, ");
            sb.append("r.address + '/' + node.name AS address, r.address AS resourceAddress, toLower(node.stability) AS stability, ");
            sb.append("coalesce(node.deprecated, false) AS deprecated, score");
        } else {
            sb.append("MATCH (a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "a", filter);
            appendDeprecatedWhere(sb, "a", filter);
            appendScopeWhere(sb, "r", filter);
            sb.append("\nRETURN 'attribute' AS type, a.name AS name, a.description AS description, ");
            sb.append("r.address + '/' + a.name AS address, r.address AS resourceAddress, toLower(a.stability) AS stability, ");
            sb.append("coalesce(a.deprecated, false) AS deprecated");
        }
        return sb.toString();
    }

    private String operationBranch(CatalogFilter filter) {
        StringBuilder sb = new StringBuilder();
        if (filter.hasSearchQuery()) {
            sb.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node, score\n");
            sb.append("WHERE node:Operation\n");
            sb.append("MATCH (node)<-[:PROVIDES]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "node", filter);
            appendDeprecatedWhere(sb, "node", filter);
            appendScopeWhere(sb, "r", filter);
            sb.append("\nRETURN 'operation' AS type, node.name AS name, node.description AS description, ");
            sb.append("r.address + '/' + node.name AS address, r.address AS resourceAddress, toLower(node.stability) AS stability, ");
            sb.append("coalesce(node.deprecated, false) AS deprecated, score");
        } else {
            sb.append("MATCH (o:Operation)<-[:PROVIDES]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "o", filter);
            appendDeprecatedWhere(sb, "o", filter);
            appendScopeWhere(sb, "r", filter);
            sb.append("\nRETURN 'operation' AS type, o.name AS name, o.description AS description, ");
            sb.append("r.address + '/' + o.name AS address, r.address AS resourceAddress, toLower(o.stability) AS stability, ");
            sb.append("coalesce(o.deprecated, false) AS deprecated");
        }
        return sb.toString();
    }

    private String capabilityBranch(CatalogFilter filter) {
        StringBuilder sb = new StringBuilder();
        // deprecated=yes excludes all capabilities (they don't have deprecated property)
        if (filter.hasDeprecatedFilter() && "yes".equals(filter.deprecated())) {
            // Return an empty branch that matches nothing
            sb.append("MATCH (c:Capability) WHERE false");
            sb.append("\nRETURN 'capability' AS type, c.name AS name, null AS description, ");
            sb.append("null AS address, null AS resourceAddress, toLower(c.stability) AS stability, ");
            sb.append("false AS deprecated");
            if (filter.hasSearchQuery()) {
                sb.append(", 0.0 AS score");
            }
            return sb.toString();
        }

        if (filter.hasSearchQuery()) {
            // Capabilities use substring matching on name segments (same as ModelGraphRepository)
            sb.append("MATCH (c:Capability)\n");
            sb.append("WHERE ALL(part IN $capabilityTerms WHERE toLower(replace(c.name, '-', '')) CONTAINS part)");
            appendStabilityWhere(sb, "c", filter);
            if (filter.hasScopeFilter()) {
                sb.append("\nWITH c");
                sb.append("\nMATCH (c)<-[:DECLARES_CAPABILITY]-(r:Resource) WHERE r.address STARTS WITH $scope");
            }
            sb.append("\nRETURN 'capability' AS type, c.name AS name, null AS description, ");
            sb.append("null AS address, null AS resourceAddress, toLower(c.stability) AS stability, ");
            sb.append("false AS deprecated, 1.0 AS score");
        } else {
            if (filter.hasScopeFilter()) {
                sb.append("MATCH (c:Capability)<-[:DECLARES_CAPABILITY]-(r:Resource) WHERE r.address STARTS WITH $scope");
                appendStabilityWhere(sb, "c", filter);
            } else {
                sb.append("MATCH (c:Capability)");
                if (filter.hasStabilityFilter()) {
                    sb.append(" WHERE toLower(c.stability) IN $stabilities");
                }
            }
            sb.append("\nRETURN 'capability' AS type, c.name AS name, null AS description, ");
            sb.append("null AS address, null AS resourceAddress, toLower(c.stability) AS stability, ");
            sb.append("false AS deprecated");
        }
        return sb.toString();
    }

    // ------------------------------------------------------ where clause helpers

    private void appendStabilityWhere(StringBuilder sb, String nodeVar, CatalogFilter filter) {
        if (filter.hasStabilityFilter()) {
            sb.append(" AND toLower(").append(nodeVar).append(".stability) IN $stabilities");
        }
    }

    private void appendDeprecatedWhere(StringBuilder sb, String nodeVar, CatalogFilter filter) {
        if (filter.hasDeprecatedFilter()) {
            boolean wantDeprecated = "yes".equals(filter.deprecated());
            sb.append(" AND coalesce(").append(nodeVar).append(".deprecated, false) = ").append(wantDeprecated);
        }
    }

    private void appendScopeWhere(StringBuilder sb, String nodeVar, CatalogFilter filter) {
        if (filter.hasScopeFilter()) {
            sb.append(" AND ").append(nodeVar).append(".address STARTS WITH $scope");
        }
    }

    // ------------------------------------------------------ type facet branches

    private void appendTypeFacetBranches(StringBuilder query, CatalogFilter filter) {
        // All four branches always included (no type filter for type facet)
        List<String> branches = List.of(
                resourceTypeFacetBranch(filter),
                attributeTypeFacetBranch(filter),
                operationTypeFacetBranch(filter),
                capabilityTypeFacetBranch(filter));
        query.append(String.join("\nUNION ALL\n", branches));
        query.append("\n");
    }

    private String resourceTypeFacetBranch(CatalogFilter filter) {
        StringBuilder sb = new StringBuilder();
        if (filter.hasSearchQuery()) {
            sb.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node\n");
            sb.append("WHERE node:Resource AND NOT node.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "node", filter);
            appendDeprecatedWhere(sb, "node", filter);
            appendScopeWhere(sb, "node", filter);
        } else {
            sb.append("MATCH (r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "r", filter);
            appendDeprecatedWhere(sb, "r", filter);
            appendScopeWhere(sb, "r", filter);
        }
        sb.append("\nRETURN 'resource' AS type");
        return sb.toString();
    }

    private String attributeTypeFacetBranch(CatalogFilter filter) {
        StringBuilder sb = new StringBuilder();
        if (filter.hasSearchQuery()) {
            sb.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node\n");
            sb.append("WHERE node:Attribute\n");
            sb.append("MATCH (node)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "node", filter);
            appendDeprecatedWhere(sb, "node", filter);
            appendScopeWhere(sb, "r", filter);
        } else {
            sb.append("MATCH (a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "a", filter);
            appendDeprecatedWhere(sb, "a", filter);
            appendScopeWhere(sb, "r", filter);
        }
        sb.append("\nRETURN 'attribute' AS type");
        return sb.toString();
    }

    private String operationTypeFacetBranch(CatalogFilter filter) {
        StringBuilder sb = new StringBuilder();
        if (filter.hasSearchQuery()) {
            sb.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node\n");
            sb.append("WHERE node:Operation\n");
            sb.append("MATCH (node)<-[:PROVIDES]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "node", filter);
            appendDeprecatedWhere(sb, "node", filter);
            appendScopeWhere(sb, "r", filter);
        } else {
            sb.append("MATCH (o:Operation)<-[:PROVIDES]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "o", filter);
            appendDeprecatedWhere(sb, "o", filter);
            appendScopeWhere(sb, "r", filter);
        }
        sb.append("\nRETURN 'operation' AS type");
        return sb.toString();
    }

    private String capabilityTypeFacetBranch(CatalogFilter filter) {
        StringBuilder sb = new StringBuilder();
        if (filter.hasDeprecatedFilter() && "yes".equals(filter.deprecated())) {
            sb.append("MATCH (c:Capability) WHERE false\nRETURN 'capability' AS type");
            return sb.toString();
        }
        if (filter.hasSearchQuery()) {
            sb.append("MATCH (c:Capability)\n");
            sb.append("WHERE ALL(part IN $capabilityTerms WHERE toLower(replace(c.name, '-', '')) CONTAINS part)");
            appendStabilityWhere(sb, "c", filter);
            if (filter.hasScopeFilter()) {
                sb.append("\nWITH c");
                sb.append("\nMATCH (c)<-[:DECLARES_CAPABILITY]-(r:Resource) WHERE r.address STARTS WITH $scope");
            }
        } else if (filter.hasScopeFilter()) {
            sb.append("MATCH (c:Capability)<-[:DECLARES_CAPABILITY]-(r:Resource) WHERE r.address STARTS WITH $scope");
            appendStabilityWhere(sb, "c", filter);
        } else {
            sb.append("MATCH (c:Capability)");
            if (filter.hasStabilityFilter()) {
                sb.append(" WHERE toLower(c.stability) IN $stabilities");
            }
        }
        sb.append("\nRETURN 'capability' AS type");
        return sb.toString();
    }

    // ------------------------------------------------------ stability facet branches

    private void appendStabilityFacetBranches(StringBuilder query, CatalogFilter filter) {
        // Only include branches matching type filter
        List<String> branches = new ArrayList<>();
        if (filter.includesType(CatalogType.RESOURCE)) {
            branches.add(resourceStabilityFacetBranch(filter));
        }
        if (filter.includesType(CatalogType.ATTRIBUTE)) {
            branches.add(attributeStabilityFacetBranch(filter));
        }
        if (filter.includesType(CatalogType.OPERATION)) {
            branches.add(operationStabilityFacetBranch(filter));
        }
        if (filter.includesType(CatalogType.CAPABILITY)) {
            branches.add(capabilityStabilityFacetBranch(filter));
        }
        query.append(String.join("\nUNION ALL\n", branches));
        query.append("\n");
    }

    private String resourceStabilityFacetBranch(CatalogFilter filter) {
        StringBuilder sb = new StringBuilder();
        if (filter.hasSearchQuery()) {
            sb.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node\n");
            sb.append("WHERE node:Resource AND NOT node.address STARTS WITH '/deployment'");
            appendDeprecatedWhere(sb, "node", filter);
            appendScopeWhere(sb, "node", filter);
            sb.append("\nRETURN toLower(node.stability) AS stability");
        } else {
            sb.append("MATCH (r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendDeprecatedWhere(sb, "r", filter);
            appendScopeWhere(sb, "r", filter);
            sb.append("\nRETURN toLower(r.stability) AS stability");
        }
        return sb.toString();
    }

    private String attributeStabilityFacetBranch(CatalogFilter filter) {
        StringBuilder sb = new StringBuilder();
        if (filter.hasSearchQuery()) {
            sb.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node\n");
            sb.append("WHERE node:Attribute\n");
            sb.append("MATCH (node)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendDeprecatedWhere(sb, "node", filter);
            appendScopeWhere(sb, "r", filter);
            sb.append("\nRETURN toLower(node.stability) AS stability");
        } else {
            sb.append("MATCH (a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendDeprecatedWhere(sb, "a", filter);
            appendScopeWhere(sb, "r", filter);
            sb.append("\nRETURN toLower(a.stability) AS stability");
        }
        return sb.toString();
    }

    private String operationStabilityFacetBranch(CatalogFilter filter) {
        StringBuilder sb = new StringBuilder();
        if (filter.hasSearchQuery()) {
            sb.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node\n");
            sb.append("WHERE node:Operation\n");
            sb.append("MATCH (node)<-[:PROVIDES]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendDeprecatedWhere(sb, "node", filter);
            appendScopeWhere(sb, "r", filter);
            sb.append("\nRETURN toLower(node.stability) AS stability");
        } else {
            sb.append("MATCH (o:Operation)<-[:PROVIDES]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendDeprecatedWhere(sb, "o", filter);
            appendScopeWhere(sb, "r", filter);
            sb.append("\nRETURN toLower(o.stability) AS stability");
        }
        return sb.toString();
    }

    private String capabilityStabilityFacetBranch(CatalogFilter filter) {
        StringBuilder sb = new StringBuilder();
        if (filter.hasDeprecatedFilter() && "yes".equals(filter.deprecated())) {
            sb.append("MATCH (c:Capability) WHERE false\nRETURN toLower(c.stability) AS stability");
            return sb.toString();
        }
        if (filter.hasSearchQuery()) {
            sb.append("MATCH (c:Capability)\n");
            sb.append("WHERE ALL(part IN $capabilityTerms WHERE toLower(replace(c.name, '-', '')) CONTAINS part)");
            if (filter.hasScopeFilter()) {
                sb.append("\nWITH c");
                sb.append("\nMATCH (c)<-[:DECLARES_CAPABILITY]-(r:Resource) WHERE r.address STARTS WITH $scope");
            }
        } else if (filter.hasScopeFilter()) {
            sb.append("MATCH (c:Capability)<-[:DECLARES_CAPABILITY]-(r:Resource) WHERE r.address STARTS WITH $scope");
        } else {
            sb.append("MATCH (c:Capability)");
        }
        sb.append("\nRETURN toLower(c.stability) AS stability");
        return sb.toString();
    }

    // ------------------------------------------------------ deprecated facet branches

    private void appendDeprecatedFacetBranches(StringBuilder query, CatalogFilter filter) {
        List<String> branches = new ArrayList<>();
        if (filter.includesType(CatalogType.RESOURCE)) {
            branches.add(resourceDeprecatedFacetBranch(filter));
        }
        if (filter.includesType(CatalogType.ATTRIBUTE)) {
            branches.add(attributeDeprecatedFacetBranch(filter));
        }
        if (filter.includesType(CatalogType.OPERATION)) {
            branches.add(operationDeprecatedFacetBranch(filter));
        }
        if (filter.includesType(CatalogType.CAPABILITY)) {
            branches.add(capabilityDeprecatedFacetBranch(filter));
        }
        query.append(String.join("\nUNION ALL\n", branches));
        query.append("\n");
    }

    private String resourceDeprecatedFacetBranch(CatalogFilter filter) {
        StringBuilder sb = new StringBuilder();
        if (filter.hasSearchQuery()) {
            sb.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node\n");
            sb.append("WHERE node:Resource AND NOT node.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "node", filter);
            appendScopeWhere(sb, "node", filter);
            sb.append("\nRETURN coalesce(node.deprecated, false) AS deprecated");
        } else {
            sb.append("MATCH (r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "r", filter);
            appendScopeWhere(sb, "r", filter);
            sb.append("\nRETURN coalesce(r.deprecated, false) AS deprecated");
        }
        return sb.toString();
    }

    private String attributeDeprecatedFacetBranch(CatalogFilter filter) {
        StringBuilder sb = new StringBuilder();
        if (filter.hasSearchQuery()) {
            sb.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node\n");
            sb.append("WHERE node:Attribute\n");
            sb.append("MATCH (node)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "node", filter);
            appendScopeWhere(sb, "r", filter);
            sb.append("\nRETURN coalesce(node.deprecated, false) AS deprecated");
        } else {
            sb.append("MATCH (a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "a", filter);
            appendScopeWhere(sb, "r", filter);
            sb.append("\nRETURN coalesce(a.deprecated, false) AS deprecated");
        }
        return sb.toString();
    }

    private String operationDeprecatedFacetBranch(CatalogFilter filter) {
        StringBuilder sb = new StringBuilder();
        if (filter.hasSearchQuery()) {
            sb.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node\n");
            sb.append("WHERE node:Operation\n");
            sb.append("MATCH (node)<-[:PROVIDES]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "node", filter);
            appendScopeWhere(sb, "r", filter);
            sb.append("\nRETURN coalesce(node.deprecated, false) AS deprecated");
        } else {
            sb.append("MATCH (o:Operation)<-[:PROVIDES]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "o", filter);
            appendScopeWhere(sb, "r", filter);
            sb.append("\nRETURN coalesce(o.deprecated, false) AS deprecated");
        }
        return sb.toString();
    }

    private String capabilityDeprecatedFacetBranch(CatalogFilter filter) {
        // Capabilities always count as not deprecated
        StringBuilder sb = new StringBuilder();
        if (filter.hasSearchQuery()) {
            sb.append("MATCH (c:Capability)\n");
            sb.append("WHERE ALL(part IN $capabilityTerms WHERE toLower(replace(c.name, '-', '')) CONTAINS part)");
            appendStabilityWhere(sb, "c", filter);
            if (filter.hasScopeFilter()) {
                sb.append("\nWITH c");
                sb.append("\nMATCH (c)<-[:DECLARES_CAPABILITY]-(r:Resource) WHERE r.address STARTS WITH $scope");
            }
        } else if (filter.hasScopeFilter()) {
            sb.append("MATCH (c:Capability)<-[:DECLARES_CAPABILITY]-(r:Resource) WHERE r.address STARTS WITH $scope");
            appendStabilityWhere(sb, "c", filter);
        } else {
            sb.append("MATCH (c:Capability)");
            if (filter.hasStabilityFilter()) {
                sb.append(" WHERE toLower(c.stability) IN $stabilities");
            }
        }
        sb.append("\nRETURN false AS deprecated");
        return sb.toString();
    }

    // ------------------------------------------------------ params and mapping

    Map<String, Object> buildParams(CatalogFilter filter) {
        Map<String, Object> params = new HashMap<>();
        params.put("limit", filter.limit());
        params.put("offset", filter.offset());
        if (filter.hasStabilityFilter()) {
            params.put("stabilities", filter.stabilities());
        }
        if (filter.hasSearchQuery()) {
            params.put("term", ModelGraphRepository.escapeAndWildcard(filter.q()));
            params.put("capabilityTerms", ModelGraphRepository.capabilityTerms(filter.q()));
        }
        if (filter.hasScopeFilter()) {
            params.put("scope", filter.scope());
        }
        return params;
    }

    private Map<String, Object> buildFacetParams(CatalogFilter filter) {
        Map<String, Object> params = new HashMap<>();
        if (filter.hasStabilityFilter()) {
            params.put("stabilities", filter.stabilities());
        }
        if (filter.hasSearchQuery()) {
            params.put("term", ModelGraphRepository.escapeAndWildcard(filter.q()));
            params.put("capabilityTerms", ModelGraphRepository.capabilityTerms(filter.q()));
        }
        if (filter.hasScopeFilter()) {
            params.put("scope", filter.scope());
        }
        return params;
    }

    private static CatalogResult toCatalogResult(Record record) {
        return new CatalogResult(
                record.get("type").asString(),
                record.get("name").asString(null),
                record.get("description").asString(null),
                record.get("address").asString(null),
                record.get("resourceAddress").asString(null),
                record.get("stability").asString(null),
                record.get("deprecated").asBoolean(false),
                null);
    }
}
