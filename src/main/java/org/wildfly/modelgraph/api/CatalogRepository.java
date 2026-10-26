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
        return facets;
    }

    private Map<String, Long> typeFacetCounts(CatalogFilter filter) {
        // Type facet: apply stability filter but NOT type filter
        StringBuilder query = new StringBuilder();
        query.append("CALL {\n");
        appendTypeFacetBranches(query, filter);
        query.append("}\n");
        query.append("RETURN type, count(*) AS count\n");

        Map<String, Object> params = new HashMap<>();
        if (filter.hasStabilityFilter()) {
            params.put("stabilities", filter.stabilities());
        }

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(query.toString(), params).list().forEach(record -> {
                counts.put(record.get("type").asString(), record.get("count").asLong());
            });
            return counts;
        }
    }

    private Map<String, Long> stabilityFacetCounts(CatalogFilter filter) {
        // Stability facet: apply type filter but NOT stability filter
        StringBuilder query = new StringBuilder();
        query.append("CALL {\n");
        appendStabilityFacetBranches(query, filter);
        query.append("}\n");
        query.append("RETURN stability, count(*) AS count\n");

        Map<String, Object> params = Map.of();

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

    // ------------------------------------------------------ query builders

    private String buildResultQuery(CatalogFilter filter) {
        StringBuilder query = new StringBuilder();
        query.append("CALL {\n");
        appendUnionBranches(query, filter);
        query.append("}\n");
        query.append("RETURN type, name, description, address, resourceAddress, stability, deprecated\n");
        query.append("ORDER BY name\n");
        query.append("SKIP $offset LIMIT $limit\n");
        return query.toString();
    }

    private String buildTotalQuery(CatalogFilter filter) {
        StringBuilder query = new StringBuilder();
        query.append("CALL {\n");
        appendUnionBranches(query, filter);
        query.append("}\n");
        query.append("RETURN count(*) AS total\n");
        return query.toString();
    }

    private void appendUnionBranches(StringBuilder query, CatalogFilter filter) {
        List<String> branches = new ArrayList<>();

        if (filter.includesType(CatalogType.RESOURCE)) {
            branches.add(resourceBranch(filter.hasStabilityFilter()));
        }
        if (filter.includesType(CatalogType.ATTRIBUTE)) {
            branches.add(attributeBranch(filter.hasStabilityFilter()));
        }
        if (filter.includesType(CatalogType.OPERATION)) {
            branches.add(operationBranch(filter.hasStabilityFilter()));
        }
        if (filter.includesType(CatalogType.CAPABILITY)) {
            branches.add(capabilityBranch(filter.hasStabilityFilter()));
        }

        query.append(String.join("\nUNION ALL\n", branches));
        query.append("\n");
    }

    private void appendTypeFacetBranches(StringBuilder query, CatalogFilter filter) {
        // All four branches always included (no type filter for type facet)
        boolean hasStability = filter.hasStabilityFilter();
        List<String> branches = List.of(
                resourceTypeFacetBranch(hasStability),
                attributeTypeFacetBranch(hasStability),
                operationTypeFacetBranch(hasStability),
                capabilityTypeFacetBranch(hasStability));
        query.append(String.join("\nUNION ALL\n", branches));
        query.append("\n");
    }

    private void appendStabilityFacetBranches(StringBuilder query, CatalogFilter filter) {
        // Only include branches matching type filter
        List<String> branches = new ArrayList<>();
        if (filter.includesType(CatalogType.RESOURCE)) {
            branches.add(resourceStabilityFacetBranch());
        }
        if (filter.includesType(CatalogType.ATTRIBUTE)) {
            branches.add(attributeStabilityFacetBranch());
        }
        if (filter.includesType(CatalogType.OPERATION)) {
            branches.add(operationStabilityFacetBranch());
        }
        if (filter.includesType(CatalogType.CAPABILITY)) {
            branches.add(capabilityStabilityFacetBranch());
        }
        query.append(String.join("\nUNION ALL\n", branches));
        query.append("\n");
    }

    // ------------------------------------------------------ result branches

    private String resourceBranch(boolean hasStability) {
        StringBuilder sb = new StringBuilder();
        sb.append("MATCH (r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
        if (hasStability) {
            sb.append(" AND toLower(r.stability) IN $stabilities");
        }
        sb.append("\nRETURN 'resource' AS type, r.name AS name, r.description AS description, ");
        sb.append("r.address AS address, null AS resourceAddress, toLower(r.stability) AS stability, ");
        sb.append("coalesce(r.deprecated, false) AS deprecated");
        return sb.toString();
    }

    private String attributeBranch(boolean hasStability) {
        StringBuilder sb = new StringBuilder();
        sb.append("MATCH (a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
        if (hasStability) {
            sb.append(" AND toLower(a.stability) IN $stabilities");
        }
        sb.append("\nRETURN 'attribute' AS type, a.name AS name, a.description AS description, ");
        sb.append("r.address + '/' + a.name AS address, r.address AS resourceAddress, toLower(a.stability) AS stability, ");
        sb.append("coalesce(a.deprecated, false) AS deprecated");
        return sb.toString();
    }

    private String operationBranch(boolean hasStability) {
        StringBuilder sb = new StringBuilder();
        sb.append("MATCH (o:Operation)<-[:PROVIDES]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
        if (hasStability) {
            sb.append(" AND toLower(o.stability) IN $stabilities");
        }
        sb.append("\nRETURN 'operation' AS type, o.name AS name, o.description AS description, ");
        sb.append("r.address + '/' + o.name AS address, r.address AS resourceAddress, toLower(o.stability) AS stability, ");
        sb.append("coalesce(o.deprecated, false) AS deprecated");
        return sb.toString();
    }

    private String capabilityBranch(boolean hasStability) {
        StringBuilder sb = new StringBuilder();
        sb.append("MATCH (c:Capability)");
        if (hasStability) {
            sb.append(" WHERE toLower(c.stability) IN $stabilities");
        }
        sb.append("\nRETURN 'capability' AS type, c.name AS name, null AS description, ");
        sb.append("null AS address, null AS resourceAddress, toLower(c.stability) AS stability, ");
        sb.append("false AS deprecated");
        return sb.toString();
    }

    // ------------------------------------------------------ type facet branches

    private String resourceTypeFacetBranch(boolean hasStability) {
        StringBuilder sb = new StringBuilder();
        sb.append("MATCH (r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
        if (hasStability) {
            sb.append(" AND toLower(r.stability) IN $stabilities");
        }
        sb.append("\nRETURN 'resource' AS type");
        return sb.toString();
    }

    private String attributeTypeFacetBranch(boolean hasStability) {
        StringBuilder sb = new StringBuilder();
        sb.append("MATCH (a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
        if (hasStability) {
            sb.append(" AND toLower(a.stability) IN $stabilities");
        }
        sb.append("\nRETURN 'attribute' AS type");
        return sb.toString();
    }

    private String operationTypeFacetBranch(boolean hasStability) {
        StringBuilder sb = new StringBuilder();
        sb.append("MATCH (o:Operation)<-[:PROVIDES]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
        if (hasStability) {
            sb.append(" AND toLower(o.stability) IN $stabilities");
        }
        sb.append("\nRETURN 'operation' AS type");
        return sb.toString();
    }

    private String capabilityTypeFacetBranch(boolean hasStability) {
        StringBuilder sb = new StringBuilder();
        sb.append("MATCH (c:Capability)");
        if (hasStability) {
            sb.append(" WHERE toLower(c.stability) IN $stabilities");
        }
        sb.append("\nRETURN 'capability' AS type");
        return sb.toString();
    }

    // ------------------------------------------------------ stability facet branches

    private String resourceStabilityFacetBranch() {
        return """
                MATCH (r:Resource) WHERE NOT r.address STARTS WITH '/deployment'
                RETURN toLower(r.stability) AS stability""";
    }

    private String attributeStabilityFacetBranch() {
        return """
                MATCH (a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'
                RETURN toLower(a.stability) AS stability""";
    }

    private String operationStabilityFacetBranch() {
        return """
                MATCH (o:Operation)<-[:PROVIDES]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'
                RETURN toLower(o.stability) AS stability""";
    }

    private String capabilityStabilityFacetBranch() {
        return """
                MATCH (c:Capability)
                RETURN toLower(c.stability) AS stability""";
    }

    // ------------------------------------------------------ params and mapping

    private Map<String, Object> buildParams(CatalogFilter filter) {
        Map<String, Object> params = new HashMap<>();
        params.put("limit", filter.limit());
        params.put("offset", filter.offset());
        if (filter.hasStabilityFilter()) {
            params.put("stabilities", filter.stabilities());
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
