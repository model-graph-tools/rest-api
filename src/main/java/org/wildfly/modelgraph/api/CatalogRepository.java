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
    private static final String SEARCH_INDEX = "mgt_search";
    private static final String NULL_OP_PROPS = ", null AS readOnly, null AS runtimeOnly, null AS isGlobal, null AS hasParams, null AS hasReturnValue";
    private static final String OP_PROPS = ", coalesce(o.`read-only`, false) AS readOnly, coalesce(o.`runtime-only`, false) AS runtimeOnly, coalesce(o.global, false) AS isGlobal, EXISTS { (o)-[:ACCEPTS]->() } AS hasParams, o.`return-value` IS NOT NULL AS hasReturnValue";
    private static final String OP_PROPS_SEARCH = ", coalesce(node.`read-only`, false) AS readOnly, coalesce(node.`runtime-only`, false) AS runtimeOnly, coalesce(node.global, false) AS isGlobal, EXISTS { (node)-[:ACCEPTS]->() } AS hasParams, node.`return-value` IS NOT NULL AS hasReturnValue";

    @Inject
    Driver driver;

    // ------------------------------------------------------ search

    public List<CatalogResult> search(CatalogFilter filter) {
        String query = buildResultQuery(filter);
        Map<String, Object> params = buildParams(filter);
        LOG.debugf("Catalog search query: %s, params: %s", query, params);

        try (var session = driver.session()) {
            return session.run(query, params).list(record -> toCatalogResult(record, filter));
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

        Set<String> applicable = applicableFacets(filter);

        // Resource-specific facets
        if (applicable.contains("singleton")) {
            facets.put("singleton", singletonFacetCounts(filter));
        }
        if (applicable.contains("declaresCap")) {
            facets.put("declaresCap", declaresCapFacetCounts(filter));
        }
        if (applicable.contains("definesAttrGroups")) {
            facets.put("definesAttrGroups", definesAttrGroupsFacetCounts(filter));
        }

        // Attribute-specific facets
        if (applicable.contains("dataType")) {
            facets.put("dataType", dataTypeFacetCounts(filter));
        }
        if (applicable.contains("accessType")) {
            facets.put("accessType", multiValueAttributeFacetCounts(filter, "accessType",
                    "toLower(a.`access-type`)", "accessTypes"));
        }
        if (applicable.contains("storage")) {
            facets.put("storage", multiValueAttributeFacetCounts(filter, "storage",
                    "toLower(a.storage)", "storageValues"));
        }
        if (applicable.contains("restart")) {
            facets.put("restart", multiValueAttributeFacetCounts(filter, "restart",
                    "toLower(a.`restart-required`)", "restartValues"));
        }
        if (applicable.contains("required")) {
            facets.put("required", booleanAttributeFacetCounts(filter, "required",
                    "a.required"));
        }
        if (applicable.contains("nillable")) {
            facets.put("nillable", booleanAttributeFacetCounts(filter, "nillable",
                    "a.nillable"));
        }
        if (applicable.contains("expressions")) {
            facets.put("expressions", booleanAttributeFacetCounts(filter, "expressions",
                    "a.`expressions-allowed`"));
        }
        if (applicable.contains("unit")) {
            facets.put("unit", multiValueAttributeFacetCounts(filter, "unit",
                    "toLower(a.unit)", "units"));
        }
        if (applicable.contains("hasAllowed")) {
            facets.put("hasAllowed", hasAllowedFacetCounts(filter));
        }
        if (applicable.contains("sensitive")) {
            facets.put("sensitive", sensitiveFacetCounts(filter));
        }
        if (applicable.contains("hasRelationships")) {
            facets.put("hasRelationships", hasRelationshipsFacetCounts(filter));
        }
        if (applicable.contains("refsCap")) {
            facets.put("refsCap", refsCapFacetCounts(filter));
        }

        // Operation-specific facets
        if (applicable.contains("readOnly")) {
            facets.put("readOnly", booleanOperationFacetCounts(filter, "readOnly", "`read-only`"));
        }
        if (applicable.contains("runtimeOnly")) {
            facets.put("runtimeOnly", booleanOperationFacetCounts(filter, "runtimeOnly", "`runtime-only`"));
        }
        if (applicable.contains("global")) {
            facets.put("global", booleanOperationFacetCounts(filter, "global", "global"));
        }
        if (applicable.contains("hasParams")) {
            facets.put("hasParams", hasParamsFacetCounts(filter));
        }
        if (applicable.contains("hasReturnValue")) {
            facets.put("hasReturnValue", hasReturnValueFacetCounts(filter));
        }

        return facets;
    }

    private Map<String, Long> typeFacetCounts(CatalogFilter filter) {
        StringBuilder query = new StringBuilder();
        query.append("CALL {\n");
        appendTypeFacetBranches(query, filter);
        query.append("}\n");
        query.append("RETURN type, count(*) AS count\n");

        Map<String, Object> params = buildFacetParams(filter);
        addAttributeFilterParams(params, filter);

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(query.toString(), params).list().forEach(record ->
                    counts.put(record.get("type").asString(), record.get("count").asLong()));
            return counts;
        }
    }

    private Map<String, Long> stabilityFacetCounts(CatalogFilter filter) {
        StringBuilder query = new StringBuilder();
        query.append("CALL {\n");
        appendStabilityFacetBranches(query, filter);
        query.append("}\n");
        query.append("RETURN stability, count(*) AS count\n");

        Map<String, Object> params = buildFacetParams(filter);
        addAttributeFilterParams(params, filter);

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
        StringBuilder query = new StringBuilder();
        query.append("CALL {\n");
        appendDeprecatedFacetBranches(query, filter);
        query.append("}\n");
        query.append("RETURN deprecated, count(*) AS count\n");

        Map<String, Object> params = buildFacetParams(filter);
        addAttributeFilterParams(params, filter);

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(query.toString(), params).list().forEach(record -> {
                boolean dep = record.get("deprecated").asBoolean(false);
                counts.merge(dep ? "yes" : "no", record.get("count").asLong(), Long::sum);
            });
            counts.putIfAbsent("yes", 0L);
            counts.putIfAbsent("no", 0L);
            return counts;
        }
    }

    private Map<String, Long> multiValueAttributeFacetCounts(CatalogFilter filter,
            String facetName, String cypherExpression, String paramName) {
        StringBuilder query = new StringBuilder();
        if (filter.hasSearchQuery()) {
            query.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node\n");
            query.append("WHERE node:Attribute\n");
            query.append("MATCH (node)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(query, "node", filter);
            appendDeprecatedWhere(query, "node", filter);
            appendScopeWhere(query, "r", filter);
            appendAttributeFiltersExcluding(query, filter, facetName, "node");
            query.append("\nWITH ").append(cypherExpression.replace("a.", "node.")).append(" AS value");
        } else {
            query.append(
                    "MATCH (a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(query, "a", filter);
            appendDeprecatedWhere(query, "a", filter);
            appendScopeWhere(query, "r", filter);
            appendAttributeFiltersExcluding(query, filter, facetName, "a");
            query.append("\nWITH ").append(cypherExpression).append(" AS value");
        }
        query.append("\nWHERE value IS NOT NULL");
        query.append("\nRETURN value, count(*) AS count");

        Map<String, Object> params = buildFacetParams(filter);
        addAttributeFilterParamsExcluding(params, filter, paramName);

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(query.toString(), params).list().forEach(record ->
                    counts.put(record.get("value").asString(), record.get("count").asLong()));
            return counts;
        }
    }

    private Map<String, Long> booleanAttributeFacetCounts(CatalogFilter filter,
            String facetName, String cypherExpression) {
        StringBuilder query = new StringBuilder();
        String expr;
        if (filter.hasSearchQuery()) {
            query.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node\n");
            query.append("WHERE node:Attribute\n");
            query.append("MATCH (node)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(query, "node", filter);
            appendDeprecatedWhere(query, "node", filter);
            appendScopeWhere(query, "r", filter);
            appendAttributeFiltersExcluding(query, filter, facetName, "node");
            expr = cypherExpression.replace("a.", "node.");
        } else {
            query.append(
                    "MATCH (a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(query, "a", filter);
            appendDeprecatedWhere(query, "a", filter);
            appendScopeWhere(query, "r", filter);
            appendAttributeFiltersExcluding(query, filter, facetName, "a");
            expr = cypherExpression;
        }
        query.append("\nRETURN CASE WHEN coalesce(").append(expr)
                .append(", false) THEN 'yes' ELSE 'no' END AS value, count(*) AS count");

        Map<String, Object> params = buildFacetParams(filter);
        addAttributeFilterParamsExcluding(params, filter, null);

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(query.toString(), params).list().forEach(record ->
                    counts.put(record.get("value").asString(), record.get("count").asLong()));
            return counts;
        }
    }

    private Map<String, Long> hasAllowedFacetCounts(CatalogFilter filter) {
        StringBuilder query = new StringBuilder();
        String nodeVar;
        if (filter.hasSearchQuery()) {
            query.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node\n");
            query.append("WHERE node:Attribute\n");
            query.append("MATCH (node)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(query, "node", filter);
            appendDeprecatedWhere(query, "node", filter);
            appendScopeWhere(query, "r", filter);
            appendAttributeFiltersExcluding(query, filter, "hasAllowed", "node");
            nodeVar = "node";
        } else {
            query.append(
                    "MATCH (a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(query, "a", filter);
            appendDeprecatedWhere(query, "a", filter);
            appendScopeWhere(query, "r", filter);
            appendAttributeFiltersExcluding(query, filter, "hasAllowed", "a");
            nodeVar = "a";
        }
        query.append("\nRETURN CASE WHEN ").append(nodeVar)
                .append(".`allowed-values` IS NOT NULL THEN 'yes' ELSE 'no' END AS value, count(*) AS count");

        Map<String, Object> params = buildFacetParams(filter);
        addAttributeFilterParamsExcluding(params, filter, null);

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(query.toString(), params).list().forEach(record ->
                    counts.put(record.get("value").asString(), record.get("count").asLong()));
            return counts;
        }
    }

    // ------------------------------------------------------ query builders

    String buildResultQuery(CatalogFilter filter) {
        StringBuilder query = new StringBuilder();
        query.append("CALL {\n");
        appendUnionBranches(query, filter);
        query.append("}\n");
        boolean includesResource = effectiveIncludesResource(filter);
        boolean includesOperation = effectiveIncludesOperation(filter);
        StringBuilder returnClause = new StringBuilder();
        returnClause.append("RETURN type, name, description, address, resourceAddress, stability, deprecated, properties");
        if (includesResource) {
            returnClause.append(", singleton, declaresCap, definesAttrGroups");
        }
        if (includesOperation) {
            returnClause.append(", readOnly, runtimeOnly, isGlobal, hasParams, hasReturnValue");
        }
        if (filter.hasSearchQuery()) {
            returnClause.append(", score");
        }
        returnClause.append("\n");
        query.append(returnClause);
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
            query.append("ORDER BY CASE WHEN address IS NULL THEN 1 ELSE 0 END, address ")
                    .append(dir).append(", name ASC\n");
        } else {
            query.append("ORDER BY ").append(sort).append(" ").append(dir).append("\n");
        }
    }

    // ------------------------------------------------------ effective type includes

    private boolean effectiveIncludesResource(CatalogFilter filter) {
        return filter.includesType(CatalogType.RESOURCE)
                && !filter.hasAnyAttributeFilter() && !filter.hasAnyOperationFilter();
    }

    private boolean effectiveIncludesAttribute(CatalogFilter filter) {
        return filter.includesType(CatalogType.ATTRIBUTE)
                && !filter.hasAnyOperationFilter() && !filter.hasAnyResourceFilter();
    }

    private boolean effectiveIncludesOperation(CatalogFilter filter) {
        return filter.includesType(CatalogType.OPERATION)
                && !filter.hasAnyAttributeFilter() && !filter.hasAnyResourceFilter();
    }

    private boolean effectiveIncludesCapability(CatalogFilter filter) {
        return filter.includesType(CatalogType.CAPABILITY)
                && !filter.hasAnyAttributeFilter() && !filter.hasAnyOperationFilter()
                && !filter.hasAnyResourceFilter();
    }

    // ------------------------------------------------------ union branches

    private void appendUnionBranches(StringBuilder query, CatalogFilter filter) {
        List<String> branches = new ArrayList<>();
        boolean includesResource = effectiveIncludesResource(filter);
        boolean includesAttribute = effectiveIncludesAttribute(filter);
        boolean includesOperation = effectiveIncludesOperation(filter);
        boolean includesCapability = effectiveIncludesCapability(filter);

        if (includesResource) {
            branches.add(resourceBranch(filter, includesOperation));
        }
        if (includesAttribute) {
            branches.add(attributeBranch(filter, includesResource, includesOperation));
        }
        if (includesOperation) {
            branches.add(operationBranch(filter, includesResource));
        }
        if (includesCapability) {
            branches.add(capabilityBranch(filter, includesResource, includesOperation));
        }

        if (branches.isEmpty()) {
            query.append("RETURN null AS type, null AS name, null AS description, null AS address, ");
            query.append("null AS resourceAddress, null AS stability, false AS deprecated, null AS properties");
            if (filter.hasSearchQuery()) {
                query.append(", 0.0 AS score");
            }
            query.append(" LIMIT 0\n");
        } else {
            query.append(String.join("\nUNION ALL\n", branches));
            query.append("\n");
        }
    }

    // ------------------------------------------------------ result branches

    private String resourceBranch(CatalogFilter filter, boolean includesOperation) {
        StringBuilder sb = new StringBuilder();
        if (filter.hasSearchQuery()) {
            sb.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node, score\n");
            sb.append("WHERE node:Resource AND NOT node.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "node", filter);
            appendDeprecatedWhere(sb, "node", filter);
            appendScopeWhere(sb, "node", filter);
            appendSingletonWhere(sb, "node", filter);
            appendDeclaresCapWhere(sb, "node", filter);
            appendDefinesAttrGroupsWhere(sb, "node", filter);
            sb.append("\nRETURN 'resource' AS type, node.name AS name, node.description AS description, ");
            sb.append("node.address AS address, null AS resourceAddress, toLower(node.stability) AS stability, ");
            sb.append("coalesce(node.deprecated, false) AS deprecated, null AS properties, ");
            sb.append("coalesce(node.singleton, false) AS singleton, ");
            sb.append("EXISTS { (node)-[:DECLARES_CAPABILITY]->() } AS declaresCap, ");
            sb.append("EXISTS { (node)-[:HAS_ATTRIBUTE]->(a:Attribute) WHERE a.`attribute-group` IS NOT NULL } AS definesAttrGroups");
            if (includesOperation) {
                sb.append(NULL_OP_PROPS);
            }
            sb.append(", score");
        } else {
            sb.append("MATCH (r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "r", filter);
            appendDeprecatedWhere(sb, "r", filter);
            appendScopeWhere(sb, "r", filter);
            appendSingletonWhere(sb, "r", filter);
            appendDeclaresCapWhere(sb, "r", filter);
            appendDefinesAttrGroupsWhere(sb, "r", filter);
            sb.append("\nRETURN 'resource' AS type, r.name AS name, r.description AS description, ");
            sb.append("r.address AS address, null AS resourceAddress, toLower(r.stability) AS stability, ");
            sb.append("coalesce(r.deprecated, false) AS deprecated, null AS properties, ");
            sb.append("coalesce(r.singleton, false) AS singleton, ");
            sb.append("EXISTS { (r)-[:DECLARES_CAPABILITY]->() } AS declaresCap, ");
            sb.append("EXISTS { (r)-[:HAS_ATTRIBUTE]->(a:Attribute) WHERE a.`attribute-group` IS NOT NULL } AS definesAttrGroups");
            if (includesOperation) {
                sb.append(NULL_OP_PROPS);
            }
        }
        return sb.toString();
    }

    private String attributeBranch(CatalogFilter filter, boolean includesResource, boolean includesOperation) {
        StringBuilder sb = new StringBuilder();
        if (filter.hasSearchQuery()) {
            sb.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node, score\n");
            sb.append("WHERE node:Attribute\n");
            sb.append("MATCH (node)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "node", filter);
            appendDeprecatedWhere(sb, "node", filter);
            appendScopeWhere(sb, "r", filter);
            appendAttributeFilters(sb, filter, "node");
            sb.append("\nRETURN 'attribute' AS type, node.name AS name, node.description AS description, ");
            sb.append("r.address + '/' + node.name AS address, r.address AS resourceAddress, toLower(node.stability) AS stability, ");
            sb.append("coalesce(node.deprecated, false) AS deprecated, ");
            sb.append("{dataType: CASE WHEN EXISTS { (node)-[:CONSISTS_OF]->() } THEN 'complex' ELSE toLower(node.type) END, accessType: toLower(node.`access-type`), ");
            sb.append("storage: toLower(node.storage), restart: toLower(node.`restart-required`), ");
            sb.append("required: coalesce(node.required, false), nillable: coalesce(node.nillable, false), ");
            sb.append("expressions: coalesce(node.`expressions-allowed`, false), ");
            sb.append("sensitive: EXISTS { (node)-[:IS_SENSITIVE]->() }, unit: toLower(node.unit)} AS properties");
            if (includesResource) {
                sb.append(", null AS singleton, null AS declaresCap, null AS definesAttrGroups");
            }
            if (includesOperation) {
                sb.append(NULL_OP_PROPS);
            }
            sb.append(", score");
        } else {
            sb.append(
                    "MATCH (a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "a", filter);
            appendDeprecatedWhere(sb, "a", filter);
            appendScopeWhere(sb, "r", filter);
            appendAttributeFilters(sb, filter, "a");
            sb.append("\nRETURN 'attribute' AS type, a.name AS name, a.description AS description, ");
            sb.append("r.address + '/' + a.name AS address, r.address AS resourceAddress, toLower(a.stability) AS stability, ");
            sb.append("coalesce(a.deprecated, false) AS deprecated, ");
            sb.append("{dataType: CASE WHEN EXISTS { (a)-[:CONSISTS_OF]->() } THEN 'complex' ELSE toLower(a.type) END, accessType: toLower(a.`access-type`), ");
            sb.append("storage: toLower(a.storage), restart: toLower(a.`restart-required`), ");
            sb.append("required: coalesce(a.required, false), nillable: coalesce(a.nillable, false), ");
            sb.append("expressions: coalesce(a.`expressions-allowed`, false), ");
            sb.append("sensitive: EXISTS { (a)-[:IS_SENSITIVE]->() }, unit: toLower(a.unit)} AS properties");
            if (includesResource) {
                sb.append(", null AS singleton, null AS declaresCap, null AS definesAttrGroups");
            }
            if (includesOperation) {
                sb.append(NULL_OP_PROPS);
            }
        }
        return sb.toString();
    }

    private String operationBranch(CatalogFilter filter, boolean includesResource) {
        StringBuilder sb = new StringBuilder();
        if (filter.hasSearchQuery()) {
            sb.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node, score\n");
            sb.append("WHERE node:Operation\n");
            sb.append("MATCH (node)<-[:PROVIDES]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "node", filter);
            appendDeprecatedWhere(sb, "node", filter);
            appendScopeWhere(sb, "r", filter);
            appendOperationFilters(sb, "node", filter);
            sb.append("\nRETURN 'operation' AS type, node.name AS name, node.description AS description, ");
            sb.append("r.address + '/' + node.name AS address, r.address AS resourceAddress, toLower(node.stability) AS stability, ");
            sb.append("coalesce(node.deprecated, false) AS deprecated, null AS properties");
            if (includesResource) {
                sb.append(", null AS singleton, null AS declaresCap, null AS definesAttrGroups");
            }
            sb.append(OP_PROPS_SEARCH);
            sb.append(", score");
        } else {
            sb.append("MATCH (o:Operation)<-[:PROVIDES]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "o", filter);
            appendDeprecatedWhere(sb, "o", filter);
            appendScopeWhere(sb, "r", filter);
            appendOperationFilters(sb, "o", filter);
            sb.append("\nRETURN 'operation' AS type, o.name AS name, o.description AS description, ");
            sb.append("r.address + '/' + o.name AS address, r.address AS resourceAddress, toLower(o.stability) AS stability, ");
            sb.append("coalesce(o.deprecated, false) AS deprecated, null AS properties");
            if (includesResource) {
                sb.append(", null AS singleton, null AS declaresCap, null AS definesAttrGroups");
            }
            sb.append(OP_PROPS);
        }
        return sb.toString();
    }

    private String capabilityBranch(CatalogFilter filter, boolean includesResource, boolean includesOperation) {
        StringBuilder sb = new StringBuilder();
        if (filter.hasDeprecatedFilter() && "yes".equals(filter.deprecated())) {
            sb.append("MATCH (c:Capability) WHERE false");
            sb.append("\nRETURN 'capability' AS type, c.name AS name, null AS description, ");
            sb.append("null AS address, null AS resourceAddress, toLower(c.stability) AS stability, ");
            sb.append("false AS deprecated, null AS properties");
            if (includesResource) {
                sb.append(", null AS singleton, null AS declaresCap, null AS definesAttrGroups");
            }
            if (includesOperation) {
                sb.append(NULL_OP_PROPS);
            }
            if (filter.hasSearchQuery()) {
                sb.append(", 0.0 AS score");
            }
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
            sb.append("\nRETURN 'capability' AS type, c.name AS name, null AS description, ");
            sb.append("null AS address, null AS resourceAddress, toLower(c.stability) AS stability, ");
            sb.append("false AS deprecated, null AS properties");
            if (includesResource) {
                sb.append(", null AS singleton, null AS declaresCap, null AS definesAttrGroups");
            }
            if (includesOperation) {
                sb.append(NULL_OP_PROPS);
            }
            sb.append(", 1.0 AS score");
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
            sb.append("false AS deprecated, null AS properties");
            if (includesResource) {
                sb.append(", null AS singleton, null AS declaresCap, null AS definesAttrGroups");
            }
            if (includesOperation) {
                sb.append(NULL_OP_PROPS);
            }
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

    private void appendSingletonWhere(StringBuilder sb, String nodeVar, CatalogFilter filter) {
        if (filter.hasSingletonFilter()) {
            sb.append(" AND coalesce(").append(nodeVar).append(".singleton, false) = $singleton");
        }
    }

    private void appendDeclaresCapWhere(StringBuilder sb, String nodeVar, CatalogFilter filter) {
        if (!filter.hasDeclaresCapFilter()) {
            return;
        }
        if (filter.declaresCapIsYes()) {
            sb.append(" AND EXISTS { (").append(nodeVar).append(")-[:DECLARES_CAPABILITY]->() }");
        } else if (filter.declaresCapIsNo()) {
            sb.append(" AND NOT EXISTS { (").append(nodeVar).append(")-[:DECLARES_CAPABILITY]->() }");
        } else {
            String text = filter.declaresCapContains();
            if (text != null) {
                sb.append(" AND EXISTS { (").append(nodeVar)
                        .append(")-[:DECLARES_CAPABILITY]->(cap:Capability) WHERE toLower(cap.name) CONTAINS $declaresCapText }");
            }
        }
    }

    private void appendDefinesAttrGroupsWhere(StringBuilder sb, String nodeVar, CatalogFilter filter) {
        if (filter.hasDefinesAttrGroupsFilter()) {
            if (filter.definesAttrGroups()) {
                sb.append(" AND EXISTS { (").append(nodeVar)
                        .append(")-[:HAS_ATTRIBUTE]->(a:Attribute) WHERE a.`attribute-group` IS NOT NULL }");
            } else {
                sb.append(" AND NOT EXISTS { (").append(nodeVar)
                        .append(")-[:HAS_ATTRIBUTE]->(a:Attribute) WHERE a.`attribute-group` IS NOT NULL }");
            }
        }
    }

    // ------------------------------------------------------ relationship-based attribute where helpers

    private void appendDataTypeFilter(StringBuilder sb, CatalogFilter filter, String nodeVar) {
        if (!filter.hasDataTypeFilter()) {
            return;
        }
        List<String> dataTypes = filter.dataTypes();
        boolean includesComplex = dataTypes.contains("complex");
        List<String> scalarTypes = dataTypes.stream().filter(t -> !"complex".equals(t)).toList();

        if (includesComplex && scalarTypes.isEmpty()) {
            sb.append(" AND EXISTS { (").append(nodeVar).append(")-[:CONSISTS_OF]->() }");
        } else if (includesComplex) {
            sb.append(" AND (EXISTS { (").append(nodeVar).append(")-[:CONSISTS_OF]->() } OR toLower(")
                    .append(nodeVar).append(".type) IN $dataTypes)");
        } else {
            sb.append(" AND toLower(").append(nodeVar).append(".type) IN $dataTypes");
        }
    }

    private void appendSensitiveWhere(StringBuilder sb, String nodeVar, CatalogFilter filter) {
        if (filter.hasSensitiveFilter()) {
            if (filter.sensitive()) {
                sb.append(" AND EXISTS { (").append(nodeVar).append(")-[:IS_SENSITIVE]->() }");
            } else {
                sb.append(" AND NOT EXISTS { (").append(nodeVar).append(")-[:IS_SENSITIVE]->() }");
            }
        }
    }

    private void appendHasRelationshipsWhere(StringBuilder sb, String nodeVar, CatalogFilter filter) {
        if (filter.hasHasRelationshipsFilter()) {
            if (filter.hasRelationships()) {
                sb.append(" AND (EXISTS { (").append(nodeVar).append(")-[:REQUIRES]->() } OR EXISTS { (")
                        .append(nodeVar).append(")-[:ALTERNATIVE]->() })");
            } else {
                sb.append(" AND NOT EXISTS { (").append(nodeVar).append(")-[:REQUIRES]->() } AND NOT EXISTS { (")
                        .append(nodeVar).append(")-[:ALTERNATIVE]->() }");
            }
        }
    }

    private void appendRefsCapWhere(StringBuilder sb, String nodeVar, CatalogFilter filter) {
        if (!filter.hasRefsCapFilter()) {
            return;
        }
        if (filter.refsCapIsYes()) {
            sb.append(" AND EXISTS { (").append(nodeVar).append(")-[:REFERENCES_CAPABILITY]->() }");
        } else if (filter.refsCapIsNo()) {
            sb.append(" AND NOT EXISTS { (").append(nodeVar).append(")-[:REFERENCES_CAPABILITY]->() }");
        } else {
            String text = filter.refsCapContains();
            if (text != null) {
                sb.append(" AND EXISTS { (").append(nodeVar)
                        .append(")-[:REFERENCES_CAPABILITY]->(cap:Capability) WHERE toLower(cap.name) CONTAINS $refsCapText }");
            }
        }
    }

    // ------------------------------------------------------ attribute filter helpers

    private void appendAttributeFilters(StringBuilder sb, CatalogFilter filter, String nodeVar) {
        appendDataTypeFilter(sb, filter, nodeVar);
        if (filter.hasAccessTypeFilter()) {
            sb.append(" AND toLower(").append(nodeVar).append(".`access-type`) IN $accessTypes");
        }
        if (filter.hasStorageFilter()) {
            sb.append(" AND toLower(").append(nodeVar).append(".storage) IN $storageValues");
        }
        if (filter.hasRestartFilter()) {
            sb.append(" AND toLower(").append(nodeVar).append(".`restart-required`) IN $restartValues");
        }
        if (filter.hasRequiredFilter()) {
            sb.append(" AND coalesce(").append(nodeVar).append(".required, false) = $required");
        }
        if (filter.hasNillableFilter()) {
            sb.append(" AND coalesce(").append(nodeVar).append(".nillable, false) = $nillable");
        }
        if (filter.hasExpressionsFilter()) {
            sb.append(" AND coalesce(").append(nodeVar).append(".`expressions-allowed`, false) = $expressions");
        }
        if (filter.hasUnitFilter()) {
            sb.append(" AND toLower(").append(nodeVar).append(".unit) IN $units");
        }
        if (filter.hasHasAllowedFilter()) {
            if (Boolean.TRUE.equals(filter.hasAllowed())) {
                sb.append(" AND ").append(nodeVar).append(".`allowed-values` IS NOT NULL");
            } else {
                sb.append(" AND ").append(nodeVar).append(".`allowed-values` IS NULL");
            }
        }
        appendSensitiveWhere(sb, nodeVar, filter);
        appendHasRelationshipsWhere(sb, nodeVar, filter);
        appendRefsCapWhere(sb, nodeVar, filter);
    }

    private void appendAttributeFiltersExcluding(StringBuilder sb, CatalogFilter filter,
            String excludeFacet, String nodeVar) {
        if (!"dataType".equals(excludeFacet)) {
            appendDataTypeFilter(sb, filter, nodeVar);
        }
        if (filter.hasAccessTypeFilter() && !"accessType".equals(excludeFacet)) {
            sb.append(" AND toLower(").append(nodeVar).append(".`access-type`) IN $accessTypes");
        }
        if (filter.hasStorageFilter() && !"storage".equals(excludeFacet)) {
            sb.append(" AND toLower(").append(nodeVar).append(".storage) IN $storageValues");
        }
        if (filter.hasRestartFilter() && !"restart".equals(excludeFacet)) {
            sb.append(" AND toLower(").append(nodeVar).append(".`restart-required`) IN $restartValues");
        }
        if (filter.hasRequiredFilter() && !"required".equals(excludeFacet)) {
            sb.append(" AND coalesce(").append(nodeVar).append(".required, false) = $required");
        }
        if (filter.hasNillableFilter() && !"nillable".equals(excludeFacet)) {
            sb.append(" AND coalesce(").append(nodeVar).append(".nillable, false) = $nillable");
        }
        if (filter.hasExpressionsFilter() && !"expressions".equals(excludeFacet)) {
            sb.append(" AND coalesce(").append(nodeVar).append(".`expressions-allowed`, false) = $expressions");
        }
        if (filter.hasUnitFilter() && !"unit".equals(excludeFacet)) {
            sb.append(" AND toLower(").append(nodeVar).append(".unit) IN $units");
        }
        if (filter.hasHasAllowedFilter() && !"hasAllowed".equals(excludeFacet)) {
            if (Boolean.TRUE.equals(filter.hasAllowed())) {
                sb.append(" AND ").append(nodeVar).append(".`allowed-values` IS NOT NULL");
            } else {
                sb.append(" AND ").append(nodeVar).append(".`allowed-values` IS NULL");
            }
        }
        if (!"sensitive".equals(excludeFacet)) {
            appendSensitiveWhere(sb, nodeVar, filter);
        }
        if (!"hasRelationships".equals(excludeFacet)) {
            appendHasRelationshipsWhere(sb, nodeVar, filter);
        }
        if (!"refsCap".equals(excludeFacet)) {
            appendRefsCapWhere(sb, nodeVar, filter);
        }
    }

    // ------------------------------------------------------ operation filter helpers

    private void appendOperationFilters(StringBuilder sb, String nodeVar, CatalogFilter filter) {
        if (filter.hasReadOnlyFilter()) {
            sb.append(" AND coalesce(").append(nodeVar).append(".`read-only`, false) = ").append(filter.readOnly());
        }
        if (filter.hasRuntimeOnlyFilter()) {
            sb.append(" AND coalesce(").append(nodeVar).append(".`runtime-only`, false) = ").append(filter.runtimeOnly());
        }
        if (filter.hasGlobalFilter()) {
            sb.append(" AND coalesce(").append(nodeVar).append(".global, false) = ").append(filter.global());
        }
        if (filter.hasParamsFilter()) {
            if (filter.hasParams()) {
                sb.append(" AND EXISTS { (").append(nodeVar).append(")-[:ACCEPTS]->() }");
            } else {
                sb.append(" AND NOT EXISTS { (").append(nodeVar).append(")-[:ACCEPTS]->() }");
            }
        }
        if (filter.hasReturnValueFilter()) {
            if (filter.hasReturnValue()) {
                sb.append(" AND ").append(nodeVar).append(".`return-value` IS NOT NULL");
            } else {
                sb.append(" AND ").append(nodeVar).append(".`return-value` IS NULL");
            }
        }
    }

    private void appendOperationFiltersExcluding(StringBuilder sb, String nodeVar,
            CatalogFilter filter, String excludeFacet) {
        if (!"readOnly".equals(excludeFacet) && filter.hasReadOnlyFilter()) {
            sb.append(" AND coalesce(").append(nodeVar).append(".`read-only`, false) = ").append(filter.readOnly());
        }
        if (!"runtimeOnly".equals(excludeFacet) && filter.hasRuntimeOnlyFilter()) {
            sb.append(" AND coalesce(").append(nodeVar).append(".`runtime-only`, false) = ").append(filter.runtimeOnly());
        }
        if (!"global".equals(excludeFacet) && filter.hasGlobalFilter()) {
            sb.append(" AND coalesce(").append(nodeVar).append(".global, false) = ").append(filter.global());
        }
        if (!"hasParams".equals(excludeFacet) && filter.hasParamsFilter()) {
            if (filter.hasParams()) {
                sb.append(" AND EXISTS { (").append(nodeVar).append(")-[:ACCEPTS]->() }");
            } else {
                sb.append(" AND NOT EXISTS { (").append(nodeVar).append(")-[:ACCEPTS]->() }");
            }
        }
        if (!"hasReturnValue".equals(excludeFacet) && filter.hasReturnValueFilter()) {
            if (filter.hasReturnValue()) {
                sb.append(" AND ").append(nodeVar).append(".`return-value` IS NOT NULL");
            } else {
                sb.append(" AND ").append(nodeVar).append(".`return-value` IS NULL");
            }
        }
    }

    // ------------------------------------------------------ type facet branches

    private void appendTypeFacetBranches(StringBuilder query, CatalogFilter filter) {
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
            appendSingletonWhere(sb, "node", filter);
            appendDeclaresCapWhere(sb, "node", filter);
            appendDefinesAttrGroupsWhere(sb, "node", filter);
        } else {
            sb.append("MATCH (r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "r", filter);
            appendDeprecatedWhere(sb, "r", filter);
            appendScopeWhere(sb, "r", filter);
            appendSingletonWhere(sb, "r", filter);
            appendDeclaresCapWhere(sb, "r", filter);
            appendDefinesAttrGroupsWhere(sb, "r", filter);
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
            appendAttributeFilters(sb, filter, "node");
        } else {
            sb.append("MATCH (a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "a", filter);
            appendDeprecatedWhere(sb, "a", filter);
            appendScopeWhere(sb, "r", filter);
            appendAttributeFilters(sb, filter, "a");
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
            appendOperationFilters(sb, "node", filter);
        } else {
            sb.append("MATCH (o:Operation)<-[:PROVIDES]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "o", filter);
            appendDeprecatedWhere(sb, "o", filter);
            appendScopeWhere(sb, "r", filter);
            appendOperationFilters(sb, "o", filter);
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
            appendSingletonWhere(sb, "node", filter);
            appendDeclaresCapWhere(sb, "node", filter);
            appendDefinesAttrGroupsWhere(sb, "node", filter);
            sb.append("\nRETURN toLower(node.stability) AS stability");
        } else {
            sb.append("MATCH (r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendDeprecatedWhere(sb, "r", filter);
            appendScopeWhere(sb, "r", filter);
            appendSingletonWhere(sb, "r", filter);
            appendDeclaresCapWhere(sb, "r", filter);
            appendDefinesAttrGroupsWhere(sb, "r", filter);
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
            appendAttributeFilters(sb, filter, "node");
            sb.append("\nRETURN toLower(node.stability) AS stability");
        } else {
            sb.append("MATCH (a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendDeprecatedWhere(sb, "a", filter);
            appendScopeWhere(sb, "r", filter);
            appendAttributeFilters(sb, filter, "a");
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
            appendOperationFilters(sb, "node", filter);
            sb.append("\nRETURN toLower(node.stability) AS stability");
        } else {
            sb.append("MATCH (o:Operation)<-[:PROVIDES]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendDeprecatedWhere(sb, "o", filter);
            appendScopeWhere(sb, "r", filter);
            appendOperationFilters(sb, "o", filter);
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
            appendSingletonWhere(sb, "node", filter);
            appendDeclaresCapWhere(sb, "node", filter);
            appendDefinesAttrGroupsWhere(sb, "node", filter);
            sb.append("\nRETURN coalesce(node.deprecated, false) AS deprecated");
        } else {
            sb.append("MATCH (r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "r", filter);
            appendScopeWhere(sb, "r", filter);
            appendSingletonWhere(sb, "r", filter);
            appendDeclaresCapWhere(sb, "r", filter);
            appendDefinesAttrGroupsWhere(sb, "r", filter);
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
            appendAttributeFilters(sb, filter, "node");
            sb.append("\nRETURN coalesce(node.deprecated, false) AS deprecated");
        } else {
            sb.append("MATCH (a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "a", filter);
            appendScopeWhere(sb, "r", filter);
            appendAttributeFilters(sb, filter, "a");
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
            appendOperationFilters(sb, "node", filter);
            sb.append("\nRETURN coalesce(node.deprecated, false) AS deprecated");
        } else {
            sb.append("MATCH (o:Operation)<-[:PROVIDES]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "o", filter);
            appendScopeWhere(sb, "r", filter);
            appendOperationFilters(sb, "o", filter);
            sb.append("\nRETURN coalesce(o.deprecated, false) AS deprecated");
        }
        return sb.toString();
    }

    private String capabilityDeprecatedFacetBranch(CatalogFilter filter) {
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

    // ------------------------------------------------------ resource-specific facet counts

    private Map<String, Long> singletonFacetCounts(CatalogFilter filter) {
        StringBuilder sb = new StringBuilder();
        if (filter.hasSearchQuery()) {
            sb.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node\n");
            sb.append("WHERE node:Resource AND NOT node.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "node", filter);
            appendDeprecatedWhere(sb, "node", filter);
            appendScopeWhere(sb, "node", filter);
            appendDeclaresCapWhere(sb, "node", filter);
            appendDefinesAttrGroupsWhere(sb, "node", filter);
            sb.append("\nRETURN CASE WHEN coalesce(node.singleton, false) THEN 'yes' ELSE 'no' END AS value, count(*) AS count");
        } else {
            sb.append("MATCH (r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "r", filter);
            appendDeprecatedWhere(sb, "r", filter);
            appendScopeWhere(sb, "r", filter);
            appendDeclaresCapWhere(sb, "r", filter);
            appendDefinesAttrGroupsWhere(sb, "r", filter);
            sb.append("\nRETURN CASE WHEN coalesce(r.singleton, false) THEN 'yes' ELSE 'no' END AS value, count(*) AS count");
        }

        Map<String, Object> params = buildFacetParams(filter);
        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(sb.toString(), params).list().forEach(record ->
                    counts.put(record.get("value").asString(), record.get("count").asLong()));
            counts.putIfAbsent("yes", 0L);
            counts.putIfAbsent("no", 0L);
            return counts;
        }
    }

    private Map<String, Long> declaresCapFacetCounts(CatalogFilter filter) {
        StringBuilder sb = new StringBuilder();
        if (filter.hasSearchQuery()) {
            sb.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node\n");
            sb.append("WHERE node:Resource AND NOT node.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "node", filter);
            appendDeprecatedWhere(sb, "node", filter);
            appendScopeWhere(sb, "node", filter);
            appendSingletonWhere(sb, "node", filter);
            appendDefinesAttrGroupsWhere(sb, "node", filter);
            sb.append("\nRETURN CASE WHEN EXISTS { (node)-[:DECLARES_CAPABILITY]->() } THEN 'yes' ELSE 'no' END AS value, count(*) AS count");
        } else {
            sb.append("MATCH (r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "r", filter);
            appendDeprecatedWhere(sb, "r", filter);
            appendScopeWhere(sb, "r", filter);
            appendSingletonWhere(sb, "r", filter);
            appendDefinesAttrGroupsWhere(sb, "r", filter);
            sb.append("\nRETURN CASE WHEN EXISTS { (r)-[:DECLARES_CAPABILITY]->() } THEN 'yes' ELSE 'no' END AS value, count(*) AS count");
        }

        Map<String, Object> params = buildFacetParams(filter);
        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(sb.toString(), params).list().forEach(record ->
                    counts.put(record.get("value").asString(), record.get("count").asLong()));
            counts.putIfAbsent("yes", 0L);
            counts.putIfAbsent("no", 0L);
            return counts;
        }
    }

    private Map<String, Long> definesAttrGroupsFacetCounts(CatalogFilter filter) {
        StringBuilder sb = new StringBuilder();
        if (filter.hasSearchQuery()) {
            sb.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node\n");
            sb.append("WHERE node:Resource AND NOT node.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "node", filter);
            appendDeprecatedWhere(sb, "node", filter);
            appendScopeWhere(sb, "node", filter);
            appendSingletonWhere(sb, "node", filter);
            appendDeclaresCapWhere(sb, "node", filter);
            sb.append("\nRETURN CASE WHEN EXISTS { (node)-[:HAS_ATTRIBUTE]->(a:Attribute) WHERE a.`attribute-group` IS NOT NULL } THEN 'yes' ELSE 'no' END AS value, count(*) AS count");
        } else {
            sb.append("MATCH (r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(sb, "r", filter);
            appendDeprecatedWhere(sb, "r", filter);
            appendScopeWhere(sb, "r", filter);
            appendSingletonWhere(sb, "r", filter);
            appendDeclaresCapWhere(sb, "r", filter);
            sb.append("\nRETURN CASE WHEN EXISTS { (r)-[:HAS_ATTRIBUTE]->(a:Attribute) WHERE a.`attribute-group` IS NOT NULL } THEN 'yes' ELSE 'no' END AS value, count(*) AS count");
        }

        Map<String, Object> params = buildFacetParams(filter);
        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(sb.toString(), params).list().forEach(record ->
                    counts.put(record.get("value").asString(), record.get("count").asLong()));
            counts.putIfAbsent("yes", 0L);
            counts.putIfAbsent("no", 0L);
            return counts;
        }
    }

    // ------------------------------------------------------ relationship-based attribute facet counts

    private Map<String, Long> dataTypeFacetCounts(CatalogFilter filter) {
        StringBuilder query = new StringBuilder();
        if (filter.hasSearchQuery()) {
            query.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node\n");
            query.append("WHERE node:Attribute\n");
            query.append("MATCH (node)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(query, "node", filter);
            appendDeprecatedWhere(query, "node", filter);
            appendScopeWhere(query, "r", filter);
            appendAttributeFiltersExcluding(query, filter, "dataType", "node");
            query.append("\nWITH CASE WHEN EXISTS { (node)-[:CONSISTS_OF]->() } THEN 'complex' ELSE toLower(node.type) END AS value");
        } else {
            query.append(
                    "MATCH (a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(query, "a", filter);
            appendDeprecatedWhere(query, "a", filter);
            appendScopeWhere(query, "r", filter);
            appendAttributeFiltersExcluding(query, filter, "dataType", "a");
            query.append("\nWITH CASE WHEN EXISTS { (a)-[:CONSISTS_OF]->() } THEN 'complex' ELSE toLower(a.type) END AS value");
        }
        query.append("\nWHERE value IS NOT NULL");
        query.append("\nRETURN value, count(*) AS count");

        Map<String, Object> params = buildFacetParams(filter);
        addAttributeFilterParamsExcluding(params, filter, "dataTypes");

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(query.toString(), params).list().forEach(record ->
                    counts.put(record.get("value").asString(), record.get("count").asLong()));
            return counts;
        }
    }

    private Map<String, Long> sensitiveFacetCounts(CatalogFilter filter) {
        StringBuilder query = new StringBuilder();
        String nodeVar;
        if (filter.hasSearchQuery()) {
            query.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node\n");
            query.append("WHERE node:Attribute\n");
            query.append("MATCH (node)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(query, "node", filter);
            appendDeprecatedWhere(query, "node", filter);
            appendScopeWhere(query, "r", filter);
            appendAttributeFiltersExcluding(query, filter, "sensitive", "node");
            nodeVar = "node";
        } else {
            query.append(
                    "MATCH (a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(query, "a", filter);
            appendDeprecatedWhere(query, "a", filter);
            appendScopeWhere(query, "r", filter);
            appendAttributeFiltersExcluding(query, filter, "sensitive", "a");
            nodeVar = "a";
        }
        query.append("\nRETURN CASE WHEN EXISTS { (").append(nodeVar)
                .append(")-[:IS_SENSITIVE]->() } THEN 'yes' ELSE 'no' END AS value, count(*) AS count");

        Map<String, Object> params = buildFacetParams(filter);
        addAttributeFilterParamsExcluding(params, filter, null);

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(query.toString(), params).list().forEach(record ->
                    counts.put(record.get("value").asString(), record.get("count").asLong()));
            counts.putIfAbsent("yes", 0L);
            counts.putIfAbsent("no", 0L);
            return counts;
        }
    }

    private Map<String, Long> hasRelationshipsFacetCounts(CatalogFilter filter) {
        StringBuilder query = new StringBuilder();
        String nodeVar;
        if (filter.hasSearchQuery()) {
            query.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node\n");
            query.append("WHERE node:Attribute\n");
            query.append("MATCH (node)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(query, "node", filter);
            appendDeprecatedWhere(query, "node", filter);
            appendScopeWhere(query, "r", filter);
            appendAttributeFiltersExcluding(query, filter, "hasRelationships", "node");
            nodeVar = "node";
        } else {
            query.append(
                    "MATCH (a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(query, "a", filter);
            appendDeprecatedWhere(query, "a", filter);
            appendScopeWhere(query, "r", filter);
            appendAttributeFiltersExcluding(query, filter, "hasRelationships", "a");
            nodeVar = "a";
        }
        query.append("\nRETURN CASE WHEN EXISTS { (").append(nodeVar)
                .append(")-[:REQUIRES]->() } OR EXISTS { (").append(nodeVar)
                .append(")-[:ALTERNATIVE]->() } THEN 'yes' ELSE 'no' END AS value, count(*) AS count");

        Map<String, Object> params = buildFacetParams(filter);
        addAttributeFilterParamsExcluding(params, filter, null);

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(query.toString(), params).list().forEach(record ->
                    counts.put(record.get("value").asString(), record.get("count").asLong()));
            counts.putIfAbsent("yes", 0L);
            counts.putIfAbsent("no", 0L);
            return counts;
        }
    }

    private Map<String, Long> refsCapFacetCounts(CatalogFilter filter) {
        StringBuilder query = new StringBuilder();
        String nodeVar;
        if (filter.hasSearchQuery()) {
            query.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node\n");
            query.append("WHERE node:Attribute\n");
            query.append("MATCH (node)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(query, "node", filter);
            appendDeprecatedWhere(query, "node", filter);
            appendScopeWhere(query, "r", filter);
            appendAttributeFiltersExcluding(query, filter, "refsCap", "node");
            nodeVar = "node";
        } else {
            query.append(
                    "MATCH (a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(query, "a", filter);
            appendDeprecatedWhere(query, "a", filter);
            appendScopeWhere(query, "r", filter);
            appendAttributeFiltersExcluding(query, filter, "refsCap", "a");
            nodeVar = "a";
        }
        query.append("\nRETURN CASE WHEN EXISTS { (").append(nodeVar)
                .append(")-[:REFERENCES_CAPABILITY]->() } THEN 'yes' ELSE 'no' END AS value, count(*) AS count");

        Map<String, Object> params = buildFacetParams(filter);
        addAttributeFilterParamsExcluding(params, filter, null);

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(query.toString(), params).list().forEach(record ->
                    counts.put(record.get("value").asString(), record.get("count").asLong()));
            counts.putIfAbsent("yes", 0L);
            counts.putIfAbsent("no", 0L);
            return counts;
        }
    }

    // ------------------------------------------------------ operation facet counts

    private Map<String, Long> booleanOperationFacetCounts(CatalogFilter filter, String facetName, String propertyName) {
        StringBuilder query = new StringBuilder();
        if (filter.hasSearchQuery()) {
            query.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node\n");
            query.append("WHERE node:Operation\n");
            query.append("MATCH (node)<-[:PROVIDES]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(query, "node", filter);
            appendDeprecatedWhere(query, "node", filter);
            appendScopeWhere(query, "r", filter);
            appendOperationFiltersExcluding(query, "node", filter, facetName);
            query.append("\nRETURN coalesce(node.").append(propertyName).append(", false) AS value, count(*) AS count");
        } else {
            query.append("MATCH (o:Operation)<-[:PROVIDES]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(query, "o", filter);
            appendDeprecatedWhere(query, "o", filter);
            appendScopeWhere(query, "r", filter);
            appendOperationFiltersExcluding(query, "o", filter, facetName);
            query.append("\nRETURN coalesce(o.").append(propertyName).append(", false) AS value, count(*) AS count");
        }

        Map<String, Object> params = buildFacetParams(filter);

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(query.toString(), params).list().forEach(record -> {
                boolean value = record.get("value").asBoolean();
                counts.put(value ? "yes" : "no", record.get("count").asLong());
            });
            counts.putIfAbsent("yes", 0L);
            counts.putIfAbsent("no", 0L);
            return counts;
        }
    }

    private Map<String, Long> hasParamsFacetCounts(CatalogFilter filter) {
        StringBuilder query = new StringBuilder();
        if (filter.hasSearchQuery()) {
            query.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node\n");
            query.append("WHERE node:Operation\n");
            query.append("MATCH (node)<-[:PROVIDES]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(query, "node", filter);
            appendDeprecatedWhere(query, "node", filter);
            appendScopeWhere(query, "r", filter);
            appendOperationFiltersExcluding(query, "node", filter, "hasParams");
            query.append("\nRETURN EXISTS { (node)-[:ACCEPTS]->() } AS value, count(*) AS count");
        } else {
            query.append("MATCH (o:Operation)<-[:PROVIDES]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(query, "o", filter);
            appendDeprecatedWhere(query, "o", filter);
            appendScopeWhere(query, "r", filter);
            appendOperationFiltersExcluding(query, "o", filter, "hasParams");
            query.append("\nRETURN EXISTS { (o)-[:ACCEPTS]->() } AS value, count(*) AS count");
        }

        Map<String, Object> params = buildFacetParams(filter);

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(query.toString(), params).list().forEach(record -> {
                boolean value = record.get("value").asBoolean();
                counts.put(value ? "yes" : "no", record.get("count").asLong());
            });
            counts.putIfAbsent("yes", 0L);
            counts.putIfAbsent("no", 0L);
            return counts;
        }
    }

    private Map<String, Long> hasReturnValueFacetCounts(CatalogFilter filter) {
        StringBuilder query = new StringBuilder();
        if (filter.hasSearchQuery()) {
            query.append("CALL db.index.fulltext.queryNodes('").append(SEARCH_INDEX).append("', $term) YIELD node\n");
            query.append("WHERE node:Operation\n");
            query.append("MATCH (node)<-[:PROVIDES]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(query, "node", filter);
            appendDeprecatedWhere(query, "node", filter);
            appendScopeWhere(query, "r", filter);
            appendOperationFiltersExcluding(query, "node", filter, "hasReturnValue");
            query.append("\nRETURN node.`return-value` IS NOT NULL AS value, count(*) AS count");
        } else {
            query.append("MATCH (o:Operation)<-[:PROVIDES]-(r:Resource) WHERE NOT r.address STARTS WITH '/deployment'");
            appendStabilityWhere(query, "o", filter);
            appendDeprecatedWhere(query, "o", filter);
            appendScopeWhere(query, "r", filter);
            appendOperationFiltersExcluding(query, "o", filter, "hasReturnValue");
            query.append("\nRETURN o.`return-value` IS NOT NULL AS value, count(*) AS count");
        }

        Map<String, Object> params = buildFacetParams(filter);

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(query.toString(), params).list().forEach(record -> {
                boolean value = record.get("value").asBoolean();
                counts.put(value ? "yes" : "no", record.get("count").asLong());
            });
            counts.putIfAbsent("yes", 0L);
            counts.putIfAbsent("no", 0L);
            return counts;
        }
    }

    // ------------------------------------------------------ params and mapping

    Map<String, Object> buildParams(CatalogFilter filter) {
        Map<String, Object> params = new HashMap<>();
        params.put("limit", filter.limit());
        params.put("offset", filter.offset());
        addFilterParams(params, filter);
        addAttributeFilterParams(params, filter);
        return params;
    }

    private Map<String, Object> buildFacetParams(CatalogFilter filter) {
        Map<String, Object> params = new HashMap<>();
        addFilterParams(params, filter);
        return params;
    }

    private void addFilterParams(Map<String, Object> params, CatalogFilter filter) {
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
        if (filter.hasSingletonFilter()) {
            params.put("singleton", filter.singleton());
        }
        String declaresCapText = filter.declaresCapContains();
        if (declaresCapText != null) {
            params.put("declaresCapText", declaresCapText);
        }
        String refsCapText = filter.refsCapContains();
        if (refsCapText != null) {
            params.put("refsCapText", refsCapText);
        }
    }

    private void addAttributeFilterParams(Map<String, Object> params, CatalogFilter filter) {
        if (filter.hasDataTypeFilter()) {
            List<String> scalarTypes = filter.dataTypes().stream().filter(t -> !"complex".equals(t)).toList();
            if (!scalarTypes.isEmpty()) {
                params.put("dataTypes", scalarTypes);
            }
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
    }

    private void addAttributeFilterParamsExcluding(Map<String, Object> params,
            CatalogFilter filter, String excludeParam) {
        if (filter.hasDataTypeFilter() && !"dataTypes".equals(excludeParam)) {
            List<String> scalarTypes = filter.dataTypes().stream().filter(t -> !"complex".equals(t)).toList();
            if (!scalarTypes.isEmpty()) {
                params.put("dataTypes", scalarTypes);
            }
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
        if (filter.hasRequiredFilter() && !"required".equals(excludeParam)) {
            params.put("required", filter.required());
        }
        if (filter.hasNillableFilter() && !"nillable".equals(excludeParam)) {
            params.put("nillable", filter.nillable());
        }
        if (filter.hasExpressionsFilter() && !"expressions".equals(excludeParam)) {
            params.put("expressions", filter.expressions());
        }
        if (filter.hasUnitFilter() && !"units".equals(excludeParam)) {
            params.put("units", filter.units());
        }
    }

    private Set<String> applicableFacets(CatalogFilter filter) {
        if (!filter.hasTypeFilter()) {
            Set<String> all = new java.util.HashSet<>();
            for (CatalogType t : CatalogType.values()) {
                all.addAll(t.applicableFacets());
            }
            return all;
        }
        return filter.types().stream()
                .map(CatalogType::fromLabel)
                .flatMap(t -> t.applicableFacets().stream())
                .collect(Collectors.toSet());
    }

    private static CatalogResult toCatalogResult(Record record, CatalogFilter filter) {
        String type = record.get("type").asString();
        Properties properties = null;
        if ("resource".equals(type) && filter.includesType(CatalogType.RESOURCE)) {
            properties = new ResourceProperties(
                    record.get("singleton").asBoolean(false),
                    record.get("declaresCap").asBoolean(false),
                    record.get("definesAttrGroups").asBoolean(false));
        } else if ("attribute".equals(type)) {
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
        } else if ("operation".equals(type) && filter.includesType(CatalogType.OPERATION)) {
            properties = new OperationProperties(
                    record.get("readOnly").asBoolean(false),
                    record.get("runtimeOnly").asBoolean(false),
                    record.get("isGlobal").asBoolean(false),
                    record.get("hasParams").asBoolean(false),
                    record.get("hasReturnValue").asBoolean(false));
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
