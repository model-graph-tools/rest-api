package org.wildfly.modelgraph.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

        Set<String> applicableFacets = applicableFacets(filter);
        if (applicableFacets.contains("dataType")) {
            facets.put("dataType", multiValueAttributeFacetCounts(filter, "dataType",
                    "toLower(a.type)", "dataTypes"));
        }
        if (applicableFacets.contains("accessType")) {
            facets.put("accessType", multiValueAttributeFacetCounts(filter, "accessType",
                    "toLower(a.`access-type`)", "accessTypes"));
        }
        if (applicableFacets.contains("storage")) {
            facets.put("storage", multiValueAttributeFacetCounts(filter, "storage",
                    "toLower(a.storage)", "storageValues"));
        }
        if (applicableFacets.contains("restart")) {
            facets.put("restart", multiValueAttributeFacetCounts(filter, "restart",
                    "toLower(a.`restart-required`)", "restartValues"));
        }
        if (applicableFacets.contains("required")) {
            facets.put("required", booleanAttributeFacetCounts(filter, "required",
                    "a.required"));
        }
        if (applicableFacets.contains("nillable")) {
            facets.put("nillable", booleanAttributeFacetCounts(filter, "nillable",
                    "a.nillable"));
        }
        if (applicableFacets.contains("expressions")) {
            facets.put("expressions", booleanAttributeFacetCounts(filter, "expressions",
                    "a.`expressions-allowed`"));
        }
        if (applicableFacets.contains("unit")) {
            facets.put("unit", multiValueAttributeFacetCounts(filter, "unit",
                    "toLower(a.unit)", "units"));
        }
        if (applicableFacets.contains("hasAllowed")) {
            facets.put("hasAllowed", hasAllowedFacetCounts(filter));
        }
        return facets;
    }

    private Map<String, Long> typeFacetCounts(CatalogFilter filter) {
        StringBuilder query = new StringBuilder();
        query.append("CALL {\n");
        appendTypeFacetBranches(query, filter);
        query.append("}\n");
        query.append("RETURN type, count(*) AS count\n");

        Map<String, Object> params = buildAttributeFilterParams(filter);
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
        StringBuilder query = new StringBuilder();
        query.append("CALL {\n");
        appendStabilityFacetBranches(query, filter);
        query.append("}\n");
        query.append("RETURN stability, count(*) AS count\n");

        Map<String, Object> params = buildAttributeFilterParams(filter);

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

    private Map<String, Long> multiValueAttributeFacetCounts(CatalogFilter filter,
            String facetName, String cypherExpression, String paramName) {
        // Apply all filters EXCEPT this facet's own filter
        StringBuilder query = new StringBuilder();
        query.append(
                "MATCH (a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
        if (filter.hasStabilityFilter()) {
            query.append(" AND toLower(a.stability) IN $stabilities");
        }
        appendAttributeFiltersExcluding(query, filter, facetName);
        query.append("\nWITH ").append(cypherExpression).append(" AS value");
        query.append("\nWHERE value IS NOT NULL");
        query.append("\nRETURN value, count(*) AS count");

        Map<String, Object> params = buildAttributeFilterParamsExcluding(filter, paramName);
        if (filter.hasStabilityFilter()) {
            params.put("stabilities", filter.stabilities());
        }

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(query.toString(), params).list().forEach(record -> {
                counts.put(record.get("value").asString(), record.get("count").asLong());
            });
            return counts;
        }
    }

    private Map<String, Long> booleanAttributeFacetCounts(CatalogFilter filter,
            String facetName, String cypherExpression) {
        // Apply all filters EXCEPT this facet's own filter
        StringBuilder query = new StringBuilder();
        query.append(
                "MATCH (a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
        if (filter.hasStabilityFilter()) {
            query.append(" AND toLower(a.stability) IN $stabilities");
        }
        appendAttributeFiltersExcluding(query, filter, facetName);
        query.append("\nRETURN CASE WHEN coalesce(").append(cypherExpression)
                .append(", false) THEN 'yes' ELSE 'no' END AS value, count(*) AS count");

        Map<String, Object> params = buildAttributeFilterParamsExcluding(filter, null);
        if (filter.hasStabilityFilter()) {
            params.put("stabilities", filter.stabilities());
        }

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(query.toString(), params).list().forEach(record -> {
                counts.put(record.get("value").asString(), record.get("count").asLong());
            });
            return counts;
        }
    }

    private Map<String, Long> hasAllowedFacetCounts(CatalogFilter filter) {
        StringBuilder query = new StringBuilder();
        query.append(
                "MATCH (a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
        if (filter.hasStabilityFilter()) {
            query.append(" AND toLower(a.stability) IN $stabilities");
        }
        appendAttributeFiltersExcluding(query, filter, "hasAllowed");
        query.append(
                "\nRETURN CASE WHEN a.`allowed-values` IS NOT NULL THEN 'yes' ELSE 'no' END AS value, count(*) AS count");

        Map<String, Object> params = buildAttributeFilterParamsExcluding(filter, null);
        if (filter.hasStabilityFilter()) {
            params.put("stabilities", filter.stabilities());
        }

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(query.toString(), params).list().forEach(record -> {
                counts.put(record.get("value").asString(), record.get("count").asLong());
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
        query.append(
                "RETURN type, name, description, address, resourceAddress, stability, deprecated, properties\n");
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
            branches.add(attributeBranch(filter));
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
        boolean hasStability = filter.hasStabilityFilter();
        List<String> branches = List.of(
                resourceTypeFacetBranch(hasStability),
                attributeTypeFacetBranch(filter),
                operationTypeFacetBranch(hasStability),
                capabilityTypeFacetBranch(hasStability));
        query.append(String.join("\nUNION ALL\n", branches));
        query.append("\n");
    }

    private void appendStabilityFacetBranches(StringBuilder query, CatalogFilter filter) {
        List<String> branches = new ArrayList<>();
        if (filter.includesType(CatalogType.RESOURCE)) {
            branches.add(resourceStabilityFacetBranch());
        }
        if (filter.includesType(CatalogType.ATTRIBUTE)) {
            branches.add(attributeStabilityFacetBranch(filter));
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
        sb.append("coalesce(r.deprecated, false) AS deprecated, null AS properties");
        return sb.toString();
    }

    private String attributeBranch(CatalogFilter filter) {
        StringBuilder sb = new StringBuilder();
        sb.append(
                "MATCH (a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
        if (filter.hasStabilityFilter()) {
            sb.append(" AND toLower(a.stability) IN $stabilities");
        }
        appendAttributeFilters(sb, filter);
        sb.append("\nRETURN 'attribute' AS type, a.name AS name, a.description AS description, ");
        sb.append("r.address + '/' + a.name AS address, r.address AS resourceAddress, toLower(a.stability) AS stability, ");
        sb.append("coalesce(a.deprecated, false) AS deprecated, ");
        sb.append("{dataType: toLower(a.type), accessType: toLower(a.`access-type`), ");
        sb.append("storage: toLower(a.storage), restart: toLower(a.`restart-required`), ");
        sb.append("required: coalesce(a.required, false), nillable: coalesce(a.nillable, false), ");
        sb.append("expressions: coalesce(a.`expressions-allowed`, false), ");
        sb.append("sensitive: false, unit: toLower(a.unit)} AS properties");
        return sb.toString();
    }

    private String operationBranch(boolean hasStability) {
        StringBuilder sb = new StringBuilder();
        sb.append(
                "MATCH (o:Operation)<-[:PROVIDES]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
        if (hasStability) {
            sb.append(" AND toLower(o.stability) IN $stabilities");
        }
        sb.append("\nRETURN 'operation' AS type, o.name AS name, o.description AS description, ");
        sb.append("r.address + '/' + o.name AS address, r.address AS resourceAddress, toLower(o.stability) AS stability, ");
        sb.append("coalesce(o.deprecated, false) AS deprecated, null AS properties");
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
        sb.append("false AS deprecated, null AS properties");
        return sb.toString();
    }

    // ------------------------------------------------------ attribute filter helpers

    private void appendAttributeFilters(StringBuilder sb, CatalogFilter filter) {
        if (filter.hasDataTypeFilter()) {
            sb.append(" AND toLower(a.type) IN $dataTypes");
        }
        if (filter.hasAccessTypeFilter()) {
            sb.append(" AND toLower(a.`access-type`) IN $accessTypes");
        }
        if (filter.hasStorageFilter()) {
            sb.append(" AND toLower(a.storage) IN $storageValues");
        }
        if (filter.hasRestartFilter()) {
            sb.append(" AND toLower(a.`restart-required`) IN $restartValues");
        }
        if (filter.hasRequiredFilter()) {
            sb.append(" AND coalesce(a.required, false) = $required");
        }
        if (filter.hasNillableFilter()) {
            sb.append(" AND coalesce(a.nillable, false) = $nillable");
        }
        if (filter.hasExpressionsFilter()) {
            sb.append(" AND coalesce(a.`expressions-allowed`, false) = $expressions");
        }
        if (filter.hasUnitFilter()) {
            sb.append(" AND toLower(a.unit) IN $units");
        }
        if (filter.hasHasAllowedFilter()) {
            if (Boolean.TRUE.equals(filter.hasAllowed())) {
                sb.append(" AND a.`allowed-values` IS NOT NULL");
            } else {
                sb.append(" AND a.`allowed-values` IS NULL");
            }
        }
    }

    private void appendAttributeFiltersExcluding(StringBuilder sb, CatalogFilter filter,
            String excludeFacet) {
        if (filter.hasDataTypeFilter() && !"dataType".equals(excludeFacet)) {
            sb.append(" AND toLower(a.type) IN $dataTypes");
        }
        if (filter.hasAccessTypeFilter() && !"accessType".equals(excludeFacet)) {
            sb.append(" AND toLower(a.`access-type`) IN $accessTypes");
        }
        if (filter.hasStorageFilter() && !"storage".equals(excludeFacet)) {
            sb.append(" AND toLower(a.storage) IN $storageValues");
        }
        if (filter.hasRestartFilter() && !"restart".equals(excludeFacet)) {
            sb.append(" AND toLower(a.`restart-required`) IN $restartValues");
        }
        if (filter.hasRequiredFilter() && !"required".equals(excludeFacet)) {
            sb.append(" AND coalesce(a.required, false) = $required");
        }
        if (filter.hasNillableFilter() && !"nillable".equals(excludeFacet)) {
            sb.append(" AND coalesce(a.nillable, false) = $nillable");
        }
        if (filter.hasExpressionsFilter() && !"expressions".equals(excludeFacet)) {
            sb.append(" AND coalesce(a.`expressions-allowed`, false) = $expressions");
        }
        if (filter.hasUnitFilter() && !"unit".equals(excludeFacet)) {
            sb.append(" AND toLower(a.unit) IN $units");
        }
        if (filter.hasHasAllowedFilter() && !"hasAllowed".equals(excludeFacet)) {
            if (Boolean.TRUE.equals(filter.hasAllowed())) {
                sb.append(" AND a.`allowed-values` IS NOT NULL");
            } else {
                sb.append(" AND a.`allowed-values` IS NULL");
            }
        }
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

    private String attributeTypeFacetBranch(CatalogFilter filter) {
        StringBuilder sb = new StringBuilder();
        sb.append(
                "MATCH (a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
        if (filter.hasStabilityFilter()) {
            sb.append(" AND toLower(a.stability) IN $stabilities");
        }
        appendAttributeFilters(sb, filter);
        sb.append("\nRETURN 'attribute' AS type");
        return sb.toString();
    }

    private String operationTypeFacetBranch(boolean hasStability) {
        StringBuilder sb = new StringBuilder();
        sb.append(
                "MATCH (o:Operation)<-[:PROVIDES]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
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

    private String attributeStabilityFacetBranch(CatalogFilter filter) {
        StringBuilder sb = new StringBuilder();
        sb.append(
                "MATCH (a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
        appendAttributeFilters(sb, filter);
        sb.append("\nRETURN toLower(a.stability) AS stability");
        return sb.toString();
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
        Map<String, Object> params = buildAttributeFilterParams(filter);
        params.put("limit", filter.limit());
        params.put("offset", filter.offset());
        if (filter.hasStabilityFilter()) {
            params.put("stabilities", filter.stabilities());
        }
        return params;
    }

    private Map<String, Object> buildAttributeFilterParams(CatalogFilter filter) {
        Map<String, Object> params = new HashMap<>();
        if (filter.hasDataTypeFilter()) {
            params.put("dataTypes", filter.dataTypes());
        }
        if (filter.hasAccessTypeFilter()) {
            params.put("accessTypes", filter.accessTypes());
        }
        if (filter.hasStorageFilter()) {
            params.put("storageValues", filter.storageValues());
        }
        if (filter.hasRestartFilter()) {
            params.put("restartValues", filter.restartValues());
        }
        if (filter.hasRequiredFilter()) {
            params.put("required", filter.required());
        }
        if (filter.hasNillableFilter()) {
            params.put("nillable", filter.nillable());
        }
        if (filter.hasExpressionsFilter()) {
            params.put("expressions", filter.expressions());
        }
        if (filter.hasUnitFilter()) {
            params.put("units", filter.units());
        }
        return params;
    }

    private Map<String, Object> buildAttributeFilterParamsExcluding(CatalogFilter filter,
            String excludeParam) {
        Map<String, Object> params = new HashMap<>();
        if (filter.hasDataTypeFilter() && !"dataTypes".equals(excludeParam)) {
            params.put("dataTypes", filter.dataTypes());
        }
        if (filter.hasAccessTypeFilter() && !"accessTypes".equals(excludeParam)) {
            params.put("accessTypes", filter.accessTypes());
        }
        if (filter.hasStorageFilter() && !"storageValues".equals(excludeParam)) {
            params.put("storageValues", filter.storageValues());
        }
        if (filter.hasRestartFilter() && !"restartValues".equals(excludeParam)) {
            params.put("restartValues", filter.restartValues());
        }
        if (filter.hasRequiredFilter()) {
            params.put("required", filter.required());
        }
        if (filter.hasNillableFilter()) {
            params.put("nillable", filter.nillable());
        }
        if (filter.hasExpressionsFilter()) {
            params.put("expressions", filter.expressions());
        }
        if (filter.hasUnitFilter() && !"units".equals(excludeParam)) {
            params.put("units", filter.units());
        }
        return params;
    }

    private Set<String> applicableFacets(CatalogFilter filter) {
        if (!filter.hasTypeFilter()) {
            // All facets are applicable when no type filter
            return CatalogType.ATTRIBUTE.applicableFacets();
        }
        return filter.types().stream()
                .map(CatalogType::fromLabel)
                .flatMap(t -> t.applicableFacets().stream())
                .collect(Collectors.toSet());
    }

    private static CatalogResult toCatalogResult(Record record) {
        String type = record.get("type").asString();
        Properties properties = null;
        if ("attribute".equals(type)) {
            var propsValue = record.get("properties");
            if (!propsValue.isNull()) {
                var propsMap = propsValue.asMap();
                properties = new AttributeProperties(
                        asString(propsMap.get("dataType")),
                        asString(propsMap.get("accessType")),
                        asString(propsMap.get("storage")),
                        asString(propsMap.get("restart")),
                        asBoolean(propsMap.get("required")),
                        asBoolean(propsMap.get("nillable")),
                        asBoolean(propsMap.get("expressions")),
                        asBoolean(propsMap.get("sensitive")),
                        asString(propsMap.get("unit")));
            }
        }
        return new CatalogResult(
                type,
                record.get("name").asString(null),
                record.get("description").asString(null),
                record.get("address").asString(null),
                record.get("resourceAddress").asString(null),
                record.get("stability").asString(null),
                record.get("deprecated").asBoolean(false),
                properties);
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return false;
    }
}
