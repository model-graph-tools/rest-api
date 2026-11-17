package org.wildfly.modelgraph.api.catalog;

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
import org.wildfly.modelgraph.api.CypherQuery;
import org.wildfly.modelgraph.api.LuceneUtils;

/** Builds dynamic Cypher queries from catalog filter combinations, executes them against Neo4j, and computes per-facet counts. */
@ApplicationScoped
public class CatalogRepository {

    private static final Logger LOG = Logger.getLogger(CatalogRepository.class);
    private static final String SEARCH_INDEX = LuceneUtils.SEARCH_INDEX;
    private static final Set<String> SAFE_SORT_FIELDS = Set.of("name", "type", "resourceAddress", "stability", "score");
    private static final Set<String> SAFE_ORDER_DIRECTIONS = Set.of("ASC", "DESC");
    private static final Set<String> REFINED_DATA_TYPES = Set.of("list-simple", "list-object", "map", "object");
    private static final String NULL_OP_PROPS = ", null AS readOnly, null AS runtimeOnly, null AS isGlobal, null AS hasParams, null AS hasReturnValue";

    @Inject
    Driver driver;

    // ------------------------------------------------------ search

    public List<CatalogResult> search(CatalogQuery filter) {
        String query = buildResultQuery(filter);
        Map<String, Object> params = buildParams(filter);
        LOG.debugf("Catalog search query: %s, params: %s", query, params);

        try (var session = driver.session()) {
            return session.run(query, params).list(record -> toCatalogResult(record, filter));
        }
    }

    public int total(CatalogQuery filter) {
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

    public CatalogFacets facetCounts(CatalogQuery filter) {
        Set<String> applicable = applicableFacets(filter);

        Map<String, Long> accessTypeCounts = null;
        if (applicable.contains("accessType")) {
            accessTypeCounts = multiValueAttributeFacetCounts(filter, "accessType",
                    "toLower(%s.`access-type`)", "accessTypes");
            accessTypeCounts.putIfAbsent("read-write", 0L);
            accessTypeCounts.putIfAbsent("read-only", 0L);
            accessTypeCounts.putIfAbsent("metric", 0L);
        }
        Map<String, Long> storageCounts = null;
        if (applicable.contains("storage")) {
            storageCounts = multiValueAttributeFacetCounts(filter, "storage",
                    "toLower(%s.storage)", "storageValues");
            storageCounts.putIfAbsent("configuration", 0L);
            storageCounts.putIfAbsent("runtime", 0L);
        }
        Map<String, Long> restartCounts = null;
        if (applicable.contains("restart")) {
            restartCounts = multiValueAttributeFacetCounts(filter, "restart",
                    "toLower(%s.`restart-required`)", "restartValues");
            restartCounts.putIfAbsent("no-services", 0L);
            restartCounts.putIfAbsent("all-services", 0L);
            restartCounts.putIfAbsent("resource-services", 0L);
            restartCounts.putIfAbsent("jvm", 0L);
        }

        return new CatalogFacets(
                typeFacetCounts(filter),
                stabilityFacetCounts(filter),
                deprecatedFacetCounts(filter),
                applicable.contains("singleton") ? singletonFacetCounts(filter) : null,
                applicable.contains("declaresCap") ? declaresCapFacetCounts(filter) : null,
                applicable.contains("definesAttrGroups") ? definesAttrGroupsFacetCounts(filter) : null,
                applicable.contains("dataType") ? dataTypeFacetCounts(filter) : null,
                accessTypeCounts,
                storageCounts,
                restartCounts,
                applicable.contains("required") ? booleanAttributeFacetCounts(filter, "required", "%s.required") : null,
                applicable.contains("nillable") ? booleanAttributeFacetCounts(filter, "nillable", "%s.nillable") : null,
                applicable.contains("expressions")
                        ? booleanAttributeFacetCounts(filter, "expressions", "%s.`expressions-allowed`")
                        : null,
                applicable.contains("unit")
                        ? multiValueAttributeFacetCounts(filter, "unit", "toLower(%s.unit)", "units")
                        : null,
                applicable.contains("hasAllowed") ? hasAllowedFacetCounts(filter) : null,
                applicable.contains("sensitive") ? sensitiveFacetCounts(filter) : null,
                applicable.contains("hasRequires") ? hasRequiresFacetCounts(filter) : null,
                applicable.contains("hasAlternatives") ? hasAlternativesFacetCounts(filter) : null,
                applicable.contains("refsCap") ? refsCapFacetCounts(filter) : null,
                applicable.contains("readOnly")
                        ? booleanOperationFacetCounts(filter, "readOnly", "`read-only`")
                        : null,
                applicable.contains("runtimeOnly")
                        ? booleanOperationFacetCounts(filter, "runtimeOnly", "`runtime-only`")
                        : null,
                applicable.contains("global")
                        ? booleanOperationFacetCounts(filter, "global", "global")
                        : null,
                applicable.contains("hasParams") ? hasParamsFacetCounts(filter) : null,
                applicable.contains("hasReturnValue") ? hasReturnValueFacetCounts(filter) : null);
    }

    private Map<String, Long> typeFacetCounts(CatalogQuery filter) {
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
            counts.putIfAbsent("resource", 0L);
            counts.putIfAbsent("attribute", 0L);
            counts.putIfAbsent("operation", 0L);
            counts.putIfAbsent("capability", 0L);
            return counts;
        }
    }

    private Map<String, Long> stabilityFacetCounts(CatalogQuery filter) {
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
            counts.putIfAbsent("default", 0L);
            counts.putIfAbsent("community", 0L);
            counts.putIfAbsent("preview", 0L);
            counts.putIfAbsent("experimental", 0L);
            return counts;
        }
    }

    private Map<String, Long> deprecatedFacetCounts(CatalogQuery filter) {
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
                counts.merge(dep ? "true" : "false", record.get("count").asLong(), Long::sum);
            });
            counts.putIfAbsent("true", 0L);
            counts.putIfAbsent("false", 0L);
            return counts;
        }
    }

    private Map<String, Long> multiValueAttributeFacetCounts(CatalogQuery filter,
            String facetName, String cypherExpressionTemplate, String paramName) {
        String v = v(filter, "a");
        CypherQuery q = startRelQuery(filter, "Attribute", "a", "<-[:HAS_ATTRIBUTE]-(r:Resource)");
        appendStabilityWhere(q, v, filter);
        appendDeprecatedWhere(q, v, filter);
        appendScopeWhere(q, "r", filter);
        appendAttributeFiltersExcluding(q, filter, facetName, v);
        q.with(cypherExpressionTemplate.formatted(v) + " AS value");
        q.where("value IS NOT NULL");
        q.returning("value, count(*) AS count");

        Map<String, Object> params = buildFacetParams(filter);
        addAttributeFilterParamsExcluding(params, filter, paramName);

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(q.toString(), params).list().forEach(record ->
                    counts.put(record.get("value").asString(), record.get("count").asLong()));
            return counts;
        }
    }

    private Map<String, Long> booleanAttributeFacetCounts(CatalogQuery filter,
            String facetName, String cypherExpressionTemplate) {
        String v = v(filter, "a");
        CypherQuery q = startRelQuery(filter, "Attribute", "a", "<-[:HAS_ATTRIBUTE]-(r:Resource)");
        appendStabilityWhere(q, v, filter);
        appendDeprecatedWhere(q, v, filter);
        appendScopeWhere(q, "r", filter);
        appendAttributeFiltersExcluding(q, filter, facetName, v);
        String expr = cypherExpressionTemplate.formatted(v);
        q.returning("CASE WHEN coalesce(" + expr + ", false) THEN 'true' ELSE 'false' END AS value, count(*) AS count");

        Map<String, Object> params = buildFacetParams(filter);
        addAttributeFilterParamsExcluding(params, filter, null);

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(q.toString(), params).list().forEach(record ->
                    counts.put(record.get("value").asString(), record.get("count").asLong()));
            counts.putIfAbsent("true", 0L);
            counts.putIfAbsent("false", 0L);
            return counts;
        }
    }

    private Map<String, Long> hasAllowedFacetCounts(CatalogQuery filter) {
        String v = v(filter, "a");
        CypherQuery q = startRelQuery(filter, "Attribute", "a", "<-[:HAS_ATTRIBUTE]-(r:Resource)");
        appendStabilityWhere(q, v, filter);
        appendDeprecatedWhere(q, v, filter);
        appendScopeWhere(q, "r", filter);
        appendAttributeFiltersExcluding(q, filter, "hasAllowed", v);
        q.returning("CASE WHEN " + v + ".`allowed` IS NOT NULL THEN 'true' ELSE 'false' END AS value, count(*) AS count");

        Map<String, Object> params = buildFacetParams(filter);
        addAttributeFilterParamsExcluding(params, filter, null);

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(q.toString(), params).list().forEach(record ->
                    counts.put(record.get("value").asString(), record.get("count").asLong()));
            return counts;
        }
    }

    // ------------------------------------------------------ query builders

    String buildResultQuery(CatalogQuery filter) {
        StringBuilder query = new StringBuilder();
        query.append("CALL {\n");
        appendUnionBranches(query, filter);
        query.append("}\n");
        boolean includesResource = effectiveIncludesResource(filter);
        boolean includesOperation = effectiveIncludesOperation(filter);
        boolean includesCapability = effectiveIncludesCapability(filter);
        StringBuilder returnClause = new StringBuilder();
        returnClause.append("RETURN type, name, description, resourceAddress, stability, deprecated, properties");
        if (includesResource) {
            returnClause.append(", singleton, capabilities, definesAttrGroups");
        }
        if (includesOperation) {
            returnClause.append(", readOnly, runtimeOnly, isGlobal, hasParams, hasReturnValue");
        }
        if (includesCapability) {
            returnClause.append(", declaringResources");
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

    String buildTotalQuery(CatalogQuery filter) {
        StringBuilder query = new StringBuilder();
        query.append("CALL {\n");
        appendUnionBranches(query, filter);
        query.append("}\n");
        query.append("RETURN count(*) AS total\n");
        return query.toString();
    }

    // ------------------------------------------------------ order by

    private void appendOrderBy(StringBuilder query, CatalogQuery filter) {
        String sort = filter.sort();
        String dir = filter.order().toUpperCase();
        if (!SAFE_SORT_FIELDS.contains(sort)) {
            sort = "name";
        }
        if (!SAFE_ORDER_DIRECTIONS.contains(dir)) {
            dir = "ASC";
        }

        if ("score".equals(sort)) {
            query.append("ORDER BY score DESC, name ASC\n");
        } else if ("resourceAddress".equals(sort)) {
            query.append("ORDER BY CASE WHEN resourceAddress IS NULL THEN 1 ELSE 0 END, resourceAddress ")
                    .append(dir).append(", name ASC\n");
        } else {
            query.append("ORDER BY ").append(sort).append(" ").append(dir).append("\n");
        }
    }

    // ------------------------------------------------------ effective type includes

    private boolean effectiveIncludesResource(CatalogQuery filter) {
        return filter.includesType(CatalogType.RESOURCE)
                && !filter.hasAnyAttributeFilter() && !filter.hasAnyOperationFilter();
    }

    private boolean effectiveIncludesAttribute(CatalogQuery filter) {
        return filter.includesType(CatalogType.ATTRIBUTE)
                && !filter.hasAnyOperationFilter() && !filter.hasAnyResourceFilter();
    }

    private boolean effectiveIncludesOperation(CatalogQuery filter) {
        return filter.includesType(CatalogType.OPERATION)
                && !filter.hasAnyAttributeFilter() && !filter.hasAnyResourceFilter();
    }

    private boolean effectiveIncludesCapability(CatalogQuery filter) {
        return filter.includesType(CatalogType.CAPABILITY)
                && !filter.hasAnyAttributeFilter() && !filter.hasAnyOperationFilter()
                && !filter.hasAnyResourceFilter();
    }

    // ------------------------------------------------------ union branches

    private void appendUnionBranches(StringBuilder query, CatalogQuery filter) {
        List<String> branches = new ArrayList<>();
        boolean includesResource = effectiveIncludesResource(filter);
        boolean includesAttribute = effectiveIncludesAttribute(filter);
        boolean includesOperation = effectiveIncludesOperation(filter);
        boolean includesCapability = effectiveIncludesCapability(filter);

        if (includesResource) {
            branches.add(resourceBranch(filter, includesOperation, includesCapability));
        }
        if (includesAttribute) {
            branches.add(attributeBranch(filter, includesResource, includesOperation, includesCapability));
        }
        if (includesOperation) {
            branches.add(operationBranch(filter, includesResource, includesCapability));
        }
        if (includesCapability) {
            branches.add(capabilityBranch(filter, includesResource, includesOperation));
        }

        if (branches.isEmpty()) {
            query.append("RETURN null AS type, null AS name, null AS description, ");
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

    private String resourceBranch(CatalogQuery filter, boolean includesOperation, boolean includesCapability) {
        String v = v(filter, "r");
        CypherQuery q = startQuery(filter, "Resource", "r", true);
        appendStabilityWhere(q, v, filter);
        appendDeprecatedWhere(q, v, filter);
        appendScopeWhere(q, v, filter);
        appendSingletonWhere(q, v, filter);
        appendDeclaresCapWhere(q, v, filter);
        appendDefinesAttrGroupsWhere(q, v, filter);
        q.returning("'resource' AS type, " + v + ".name AS name, " + v + ".description AS description, "
                + v + ".address AS resourceAddress, toLower(" + v + ".stability) AS stability, "
                + "EXISTS { (" + v + ")-[:DEPRECATED_SINCE]->() } AS deprecated, null AS properties, "
                + "coalesce(" + v + ".singleton, false) AS singleton, "
                + "[(" + v + ")-[:DECLARES_CAPABILITY]->(cap) | cap.name] AS capabilities, "
                + "EXISTS { (" + v + ")-[:HAS_ATTRIBUTE]->(a:Attribute) WHERE a.`attribute-group` IS NOT NULL } AS definesAttrGroups");
        if (includesOperation) {
            q.append(NULL_OP_PROPS);
        }
        if (includesCapability) {
            q.append(", null AS declaringResources");
        }
        if (filter.hasSearchQuery()) {
            q.append(", score");
        }
        return q.toString();
    }

    private String attributeBranch(CatalogQuery filter, boolean includesResource, boolean includesOperation,
            boolean includesCapability) {
        String v = v(filter, "a");
        CypherQuery q = startRelQuery(filter, "Attribute", "a", "<-[:HAS_ATTRIBUTE]-(r:Resource)", true);
        appendStabilityWhere(q, v, filter);
        appendDeprecatedWhere(q, v, filter);
        appendScopeWhere(q, "r", filter);
        appendAttributeFilters(q, filter, v);
        q.returning("'attribute' AS type, " + v + ".name AS name, " + v + ".description AS description, "
                + "r.address AS resourceAddress, toLower(" + v + ".stability) AS stability, "
                + "EXISTS { (" + v + ")-[:DEPRECATED_SINCE]->() } AS deprecated, "
                + "{dataType: " + dataTypeCaseExpression(v) + ", accessType: toLower(" + v + ".`access-type`), "
                + "storage: toLower(" + v + ".storage), restart: toLower(" + v + ".`restart-required`), "
                + "required: coalesce(" + v + ".required, false), nillable: coalesce(" + v + ".nillable, false), "
                + "expressions: coalesce(" + v + ".`expressions-allowed`, false), "
                + "sensitive: EXISTS { (" + v + ")-[:IS_SENSITIVE]->() }, unit: toLower(" + v + ".unit), "
                + "capabilityReference: head([(" + v + ")-[:REFERENCES_CAPABILITY]->(cap) | cap.name])} AS properties");
        if (includesResource) {
            q.append(", null AS singleton, null AS capabilities, null AS definesAttrGroups");
        }
        if (includesOperation) {
            q.append(NULL_OP_PROPS);
        }
        if (includesCapability) {
            q.append(", null AS declaringResources");
        }
        if (filter.hasSearchQuery()) {
            q.append(", score");
        }
        return q.toString();
    }

    private String operationBranch(CatalogQuery filter, boolean includesResource, boolean includesCapability) {
        String v = v(filter, "o");
        CypherQuery q = startRelQuery(filter, "Operation", "o", "<-[:PROVIDES]-(r:Resource)", true);
        appendStabilityWhere(q, v, filter);
        appendDeprecatedWhere(q, v, filter);
        appendScopeWhere(q, "r", filter);
        appendOperationFilters(q, v, filter);
        q.returning("'operation' AS type, " + v + ".name AS name, " + v + ".description AS description, "
                + "r.address AS resourceAddress, toLower(" + v + ".stability) AS stability, "
                + "EXISTS { (" + v + ")-[:DEPRECATED_SINCE]->() } AS deprecated, null AS properties");
        if (includesResource) {
            q.append(", null AS singleton, null AS capabilities, null AS definesAttrGroups");
        }
        q.append(opProps(v));
        if (includesCapability) {
            q.append(", null AS declaringResources");
        }
        if (filter.hasSearchQuery()) {
            q.append(", score");
        }
        return q.toString();
    }

    private String capabilityBranch(CatalogQuery filter, boolean includesResource, boolean includesOperation) {
        CypherQuery q = CypherQuery.create();
        if (filter.hasDeprecatedFilter() && Boolean.TRUE.equals(filter.deprecated())) {
            q.match("(c:Capability)").where("false");
            q.returning("'capability' AS type, c.name AS name, null AS description, "
                    + "null AS address, null AS resourceAddress, toLower(c.stability) AS stability, "
                    + "false AS deprecated, null AS properties");
            if (includesResource) {
                q.append(", null AS singleton, null AS capabilities, null AS definesAttrGroups");
            }
            if (includesOperation) {
                q.append(NULL_OP_PROPS);
            }
            q.append(", [] AS declaringResources");
            if (filter.hasSearchQuery()) {
                q.append(", 0.0 AS score");
            }
            return q.toString();
        }

        if (filter.hasSearchQuery()) {
            q.match("(c:Capability)\n");
            q.where("ALL(part IN $capabilityTerms WHERE toLower(replace(c.name, '-', '')) CONTAINS part)");
            appendStabilityWhere(q, "c", filter);
            if (filter.hasScopeFilter()) {
                q.with("c");
                q.newLine().match("(c)<-[:DECLARES_CAPABILITY]-(r:Resource)").where("r.address STARTS WITH $scope");
            }
        } else {
            if (filter.hasScopeFilter()) {
                q.match("(c:Capability)<-[:DECLARES_CAPABILITY]-(r:Resource)").where("r.address STARTS WITH $scope");
                appendStabilityWhere(q, "c", filter);
            } else {
                q.match("(c:Capability)");
                q.whereIf(filter.hasStabilityFilter(), "toLower(c.stability) IN $stabilities");
            }
        }
        q.returning("'capability' AS type, c.name AS name, null AS description, "
                + "null AS resourceAddress, toLower(c.stability) AS stability, "
                + "false AS deprecated, null AS properties");
        if (includesResource) {
            q.append(", null AS singleton, null AS capabilities, null AS definesAttrGroups");
        }
        if (includesOperation) {
            q.append(NULL_OP_PROPS);
        }
        q.append(", [(c)<-[:DECLARES_CAPABILITY]-(dr:Resource) | dr.address] AS declaringResources");
        if (filter.hasSearchQuery()) {
            q.append(", 1.0 AS score");
        }
        return q.toString();
    }

    // ------------------------------------------------------ query start helpers

    private CypherQuery startQuery(CatalogQuery filter, String label, String var, boolean withScore) {
        CypherQuery q = CypherQuery.create();
        if (filter.hasSearchQuery()) {
            if (withScore) {
                q.fullTextSearch(SEARCH_INDEX, label);
            } else {
                q.fullTextSearchNoScore(SEARCH_INDEX, label);
            }
        } else {
            q.match("(" + var + ":" + label + ")");
        }
        return q;
    }

    private CypherQuery startQuery(CatalogQuery filter, String label, String var) {
        return startQuery(filter, label, var, false);
    }

    private CypherQuery startRelQuery(CatalogQuery filter, String label, String var, String relationship, boolean withScore) {
        CypherQuery q = CypherQuery.create();
        if (filter.hasSearchQuery()) {
            if (withScore) {
                q.fullTextSearch(SEARCH_INDEX, label);
            } else {
                q.fullTextSearchNoScore(SEARCH_INDEX, label);
            }
            q.newLine().match("(node)" + relationship);
        } else {
            q.match("(" + var + ":" + label + ")" + relationship);
        }
        return q;
    }

    private CypherQuery startRelQuery(CatalogQuery filter, String label, String var, String relationship) {
        return startRelQuery(filter, label, var, relationship, false);
    }

    private String v(CatalogQuery filter, String defaultVar) {
        return filter.hasSearchQuery() ? "node" : defaultVar;
    }

    private static String opProps(String v) {
        return ", coalesce(" + v + ".`read-only`, false) AS readOnly, coalesce(" + v
                + ".`runtime-only`, false) AS runtimeOnly, coalesce(" + v
                + ".global, false) AS isGlobal, EXISTS { (" + v
                + ")-[:ACCEPTS]->() } AS hasParams, " + v + ".`return-value` IS NOT NULL AS hasReturnValue";
    }

    // ------------------------------------------------------ where clause helpers

    private void appendStabilityWhere(CypherQuery q, String nodeVar, CatalogQuery filter) {
        q.whereIf(filter.hasStabilityFilter(), "toLower(" + nodeVar + ".stability) IN $stabilities");
    }

    private void appendDeprecatedWhere(CypherQuery q, String nodeVar, CatalogQuery filter) {
        if (filter.hasDeprecatedFilter()) {
            if (Boolean.TRUE.equals(filter.deprecated())) {
                q.where("EXISTS { (" + nodeVar + ")-[:DEPRECATED_SINCE]->() }");
            } else {
                q.where("NOT EXISTS { (" + nodeVar + ")-[:DEPRECATED_SINCE]->() }");
            }
        }
    }

    private void appendScopeWhere(CypherQuery q, String nodeVar, CatalogQuery filter) {
        q.whereIf(filter.hasScopeFilter(), nodeVar + ".address STARTS WITH $scope");
    }

    private void appendSingletonWhere(CypherQuery q, String nodeVar, CatalogQuery filter) {
        q.whereIf(filter.hasSingletonFilter(), "coalesce(" + nodeVar + ".singleton, false) = $singleton");
    }

    private void appendDeclaresCapWhere(CypherQuery q, String nodeVar, CatalogQuery filter) {
        if (!filter.hasDeclaresCapFilter()) {
            return;
        }
        if (filter.declaresCapIsTrue()) {
            q.where("EXISTS { (" + nodeVar + ")-[:DECLARES_CAPABILITY]->() }");
        } else if (filter.declaresCapIsFalse()) {
            q.where("NOT EXISTS { (" + nodeVar + ")-[:DECLARES_CAPABILITY]->() }");
        } else {
            String text = filter.declaresCapContains();
            if (text != null) {
                q.where("EXISTS { (" + nodeVar
                        + ")-[:DECLARES_CAPABILITY]->(cap:Capability) WHERE toLower(cap.name) CONTAINS $declaresCapText }");
            }
        }
    }

    private void appendDefinesAttrGroupsWhere(CypherQuery q, String nodeVar, CatalogQuery filter) {
        if (filter.hasDefinesAttrGroupsFilter()) {
            if (filter.definesAttrGroups()) {
                q.where("EXISTS { (" + nodeVar
                        + ")-[:HAS_ATTRIBUTE]->(a:Attribute) WHERE a.`attribute-group` IS NOT NULL }");
            } else {
                q.where("NOT EXISTS { (" + nodeVar
                        + ")-[:HAS_ATTRIBUTE]->(a:Attribute) WHERE a.`attribute-group` IS NOT NULL }");
            }
        }
    }

    // ------------------------------------------------------ relationship-based attribute where helpers

    private void appendDataTypeFilter(CypherQuery q, CatalogQuery filter, String nodeVar) {
        if (!filter.hasDataTypeFilter()) {
            return;
        }
        List<String> dataTypes = filter.dataTypes();
        List<String> refined = dataTypes.stream().filter(REFINED_DATA_TYPES::contains).toList();
        List<String> scalar = dataTypes.stream().filter(t -> !REFINED_DATA_TYPES.contains(t)).toList();

        List<String> conditions = new ArrayList<>();
        for (String category : refined) {
            switch (category) {
                case "list-simple" -> conditions.add("(" + nodeVar + ".type = 'LIST' AND (" + nodeVar
                        + ".`value-type` <> 'OBJECT' OR " + nodeVar + ".`value-type` IS NULL))");
                case "list-object" -> conditions.add("(" + nodeVar + ".type = 'LIST' AND " + nodeVar
                        + ".`value-type` = 'OBJECT')");
                case "map" -> conditions.add("(" + nodeVar + ".type = 'OBJECT' AND NOT EXISTS { (" + nodeVar
                        + ")-[:CONSISTS_OF]->() } AND NOT (" + nodeVar + ".`value-type` IS NULL OR " + nodeVar
                        + ".`value-type` = 'OBJECT'))");
                case "object" -> conditions.add("(" + nodeVar + ".type = 'OBJECT' AND (EXISTS { (" + nodeVar
                        + ")-[:CONSISTS_OF]->() } OR " + nodeVar + ".`value-type` IS NULL OR " + nodeVar
                        + ".`value-type` = 'OBJECT'))");
                default -> { /* unreachable — REFINED_DATA_TYPES is the filter */ }
            }
        }
        if (!scalar.isEmpty()) {
            conditions.add("toLower(" + nodeVar + ".type) IN $dataTypes");
        }

        if (conditions.size() == 1) {
            q.where(conditions.getFirst());
        } else {
            q.where("(" + String.join(" OR ", conditions) + ")");
        }
    }

    private void appendSensitiveWhere(CypherQuery q, String nodeVar, CatalogQuery filter) {
        if (filter.hasSensitiveFilter()) {
            if (filter.sensitive()) {
                q.where("EXISTS { (" + nodeVar + ")-[:IS_SENSITIVE]->() }");
            } else {
                q.where("NOT EXISTS { (" + nodeVar + ")-[:IS_SENSITIVE]->() }");
            }
        }
    }

    private void appendHasRequiresWhere(CypherQuery q, String nodeVar, CatalogQuery filter) {
        if (filter.hasHasRequiresFilter()) {
            if (filter.hasRequires()) {
                q.where("EXISTS { (" + nodeVar + ")-[:REQUIRES]->() }");
            } else {
                q.where("NOT EXISTS { (" + nodeVar + ")-[:REQUIRES]->() }");
            }
        }
    }

    private void appendHasAlternativesWhere(CypherQuery q, String nodeVar, CatalogQuery filter) {
        if (filter.hasHasAlternativesFilter()) {
            if (filter.hasAlternatives()) {
                q.where("EXISTS { (" + nodeVar + ")-[:ALTERNATIVE]->() }");
            } else {
                q.where("NOT EXISTS { (" + nodeVar + ")-[:ALTERNATIVE]->() }");
            }
        }
    }

    private void appendRefsCapWhere(CypherQuery q, String nodeVar, CatalogQuery filter) {
        if (!filter.hasRefsCapFilter()) {
            return;
        }
        if (filter.refsCapIsTrue()) {
            q.where("EXISTS { (" + nodeVar + ")-[:REFERENCES_CAPABILITY]->() }");
        } else if (filter.refsCapIsFalse()) {
            q.where("NOT EXISTS { (" + nodeVar + ")-[:REFERENCES_CAPABILITY]->() }");
        } else {
            String text = filter.refsCapContains();
            if (text != null) {
                q.where("EXISTS { (" + nodeVar
                        + ")-[:REFERENCES_CAPABILITY]->(cap:Capability) WHERE toLower(cap.name) CONTAINS $refsCapText }");
            }
        }
    }

    // ------------------------------------------------------ attribute filter helpers

    private void appendAttributeFilters(CypherQuery q, CatalogQuery filter, String nodeVar) {
        appendDataTypeFilter(q, filter, nodeVar);
        q.whereIf(filter.hasAccessTypeFilter(), "toLower(" + nodeVar + ".`access-type`) IN $accessTypes");
        q.whereIf(filter.hasStorageFilter(), "toLower(" + nodeVar + ".storage) IN $storageValues");
        q.whereIf(filter.hasRestartFilter(), "toLower(" + nodeVar + ".`restart-required`) IN $restartValues");
        q.whereIf(filter.hasRequiredFilter(), "coalesce(" + nodeVar + ".required, false) = $required");
        q.whereIf(filter.hasNillableFilter(), "coalesce(" + nodeVar + ".nillable, false) = $nillable");
        q.whereIf(filter.hasExpressionsFilter(), "coalesce(" + nodeVar + ".`expressions-allowed`, false) = $expressions");
        q.whereIf(filter.hasUnitFilter(), "toLower(" + nodeVar + ".unit) IN $units");
        if (filter.hasHasAllowedFilter()) {
            if (Boolean.TRUE.equals(filter.hasAllowed())) {
                q.where(nodeVar + ".`allowed` IS NOT NULL");
            } else {
                q.where(nodeVar + ".`allowed` IS NULL");
            }
        }
        appendSensitiveWhere(q, nodeVar, filter);
        appendHasRequiresWhere(q, nodeVar, filter);
        appendHasAlternativesWhere(q, nodeVar, filter);
        appendRefsCapWhere(q, nodeVar, filter);
    }

    private void appendAttributeFiltersExcluding(CypherQuery q, CatalogQuery filter,
            String excludeFacet, String nodeVar) {
        if (!"dataType".equals(excludeFacet)) {
            appendDataTypeFilter(q, filter, nodeVar);
        }
        if (filter.hasAccessTypeFilter() && !"accessType".equals(excludeFacet)) {
            q.where("toLower(" + nodeVar + ".`access-type`) IN $accessTypes");
        }
        if (filter.hasStorageFilter() && !"storage".equals(excludeFacet)) {
            q.where("toLower(" + nodeVar + ".storage) IN $storageValues");
        }
        if (filter.hasRestartFilter() && !"restart".equals(excludeFacet)) {
            q.where("toLower(" + nodeVar + ".`restart-required`) IN $restartValues");
        }
        if (filter.hasRequiredFilter() && !"required".equals(excludeFacet)) {
            q.where("coalesce(" + nodeVar + ".required, false) = $required");
        }
        if (filter.hasNillableFilter() && !"nillable".equals(excludeFacet)) {
            q.where("coalesce(" + nodeVar + ".nillable, false) = $nillable");
        }
        if (filter.hasExpressionsFilter() && !"expressions".equals(excludeFacet)) {
            q.where("coalesce(" + nodeVar + ".`expressions-allowed`, false) = $expressions");
        }
        if (filter.hasUnitFilter() && !"unit".equals(excludeFacet)) {
            q.where("toLower(" + nodeVar + ".unit) IN $units");
        }
        if (filter.hasHasAllowedFilter() && !"hasAllowed".equals(excludeFacet)) {
            if (Boolean.TRUE.equals(filter.hasAllowed())) {
                q.where(nodeVar + ".`allowed` IS NOT NULL");
            } else {
                q.where(nodeVar + ".`allowed` IS NULL");
            }
        }
        if (!"sensitive".equals(excludeFacet)) {
            appendSensitiveWhere(q, nodeVar, filter);
        }
        if (!"hasRequires".equals(excludeFacet)) {
            appendHasRequiresWhere(q, nodeVar, filter);
        }
        if (!"hasAlternatives".equals(excludeFacet)) {
            appendHasAlternativesWhere(q, nodeVar, filter);
        }
        if (!"refsCap".equals(excludeFacet)) {
            appendRefsCapWhere(q, nodeVar, filter);
        }
    }

    // ------------------------------------------------------ operation filter helpers

    private void appendOperationFilters(CypherQuery q, String nodeVar, CatalogQuery filter) {
        if (filter.hasReadOnlyFilter()) {
            q.where("coalesce(" + nodeVar + ".`read-only`, false) = " + filter.readOnly());
        }
        if (filter.hasRuntimeOnlyFilter()) {
            q.where("coalesce(" + nodeVar + ".`runtime-only`, false) = " + filter.runtimeOnly());
        }
        if (filter.hasGlobalFilter()) {
            q.where("coalesce(" + nodeVar + ".global, false) = " + filter.global());
        }
        if (filter.hasParamsFilter()) {
            if (filter.hasParams()) {
                q.where("EXISTS { (" + nodeVar + ")-[:ACCEPTS]->() }");
            } else {
                q.where("NOT EXISTS { (" + nodeVar + ")-[:ACCEPTS]->() }");
            }
        }
        if (filter.hasReturnValueFilter()) {
            if (filter.hasReturnValue()) {
                q.where(nodeVar + ".`return-value` IS NOT NULL");
            } else {
                q.where(nodeVar + ".`return-value` IS NULL");
            }
        }
    }

    private void appendOperationFiltersExcluding(CypherQuery q, String nodeVar,
            CatalogQuery filter, String excludeFacet) {
        if (!"readOnly".equals(excludeFacet) && filter.hasReadOnlyFilter()) {
            q.where("coalesce(" + nodeVar + ".`read-only`, false) = " + filter.readOnly());
        }
        if (!"runtimeOnly".equals(excludeFacet) && filter.hasRuntimeOnlyFilter()) {
            q.where("coalesce(" + nodeVar + ".`runtime-only`, false) = " + filter.runtimeOnly());
        }
        if (!"global".equals(excludeFacet) && filter.hasGlobalFilter()) {
            q.where("coalesce(" + nodeVar + ".global, false) = " + filter.global());
        }
        if (!"hasParams".equals(excludeFacet) && filter.hasParamsFilter()) {
            if (filter.hasParams()) {
                q.where("EXISTS { (" + nodeVar + ")-[:ACCEPTS]->() }");
            } else {
                q.where("NOT EXISTS { (" + nodeVar + ")-[:ACCEPTS]->() }");
            }
        }
        if (!"hasReturnValue".equals(excludeFacet) && filter.hasReturnValueFilter()) {
            if (filter.hasReturnValue()) {
                q.where(nodeVar + ".`return-value` IS NOT NULL");
            } else {
                q.where(nodeVar + ".`return-value` IS NULL");
            }
        }
    }

    // ------------------------------------------------------ type facet branches

    private void appendTypeFacetBranches(StringBuilder query, CatalogQuery filter) {
        List<String> branches = List.of(
                resourceTypeFacetBranch(filter),
                attributeTypeFacetBranch(filter),
                operationTypeFacetBranch(filter),
                capabilityTypeFacetBranch(filter));
        query.append(String.join("\nUNION ALL\n", branches));
        query.append("\n");
    }

    private String resourceTypeFacetBranch(CatalogQuery filter) {
        String v = v(filter, "r");
        CypherQuery q = startQuery(filter, "Resource", "r");
        appendStabilityWhere(q, v, filter);
        appendDeprecatedWhere(q, v, filter);
        appendScopeWhere(q, v, filter);
        appendSingletonWhere(q, v, filter);
        appendDeclaresCapWhere(q, v, filter);
        appendDefinesAttrGroupsWhere(q, v, filter);
        return q.returning("'resource' AS type").toString();
    }

    private String attributeTypeFacetBranch(CatalogQuery filter) {
        String v = v(filter, "a");
        CypherQuery q = startRelQuery(filter, "Attribute", "a", "<-[:HAS_ATTRIBUTE]-(r:Resource)");
        appendStabilityWhere(q, v, filter);
        appendDeprecatedWhere(q, v, filter);
        appendScopeWhere(q, "r", filter);
        appendAttributeFilters(q, filter, v);
        return q.returning("'attribute' AS type").toString();
    }

    private String operationTypeFacetBranch(CatalogQuery filter) {
        String v = v(filter, "o");
        CypherQuery q = startRelQuery(filter, "Operation", "o", "<-[:PROVIDES]-(r:Resource)");
        appendStabilityWhere(q, v, filter);
        appendDeprecatedWhere(q, v, filter);
        appendScopeWhere(q, "r", filter);
        appendOperationFilters(q, v, filter);
        return q.returning("'operation' AS type").toString();
    }

    private String capabilityTypeFacetBranch(CatalogQuery filter) {
        CypherQuery q = CypherQuery.create();
        if (filter.hasDeprecatedFilter() && Boolean.TRUE.equals(filter.deprecated())) {
            return q.match("(c:Capability)").where("false").returning("'capability' AS type").toString();
        }
        if (filter.hasSearchQuery()) {
            q.match("(c:Capability)\n");
            q.where("ALL(part IN $capabilityTerms WHERE toLower(replace(c.name, '-', '')) CONTAINS part)");
            appendStabilityWhere(q, "c", filter);
            if (filter.hasScopeFilter()) {
                q.with("c");
                q.newLine().match("(c)<-[:DECLARES_CAPABILITY]-(r:Resource)").where("r.address STARTS WITH $scope");
            }
        } else if (filter.hasScopeFilter()) {
            q.match("(c:Capability)<-[:DECLARES_CAPABILITY]-(r:Resource)").where("r.address STARTS WITH $scope");
            appendStabilityWhere(q, "c", filter);
        } else {
            q.match("(c:Capability)");
            q.whereIf(filter.hasStabilityFilter(), "toLower(c.stability) IN $stabilities");
        }
        return q.returning("'capability' AS type").toString();
    }

    // ------------------------------------------------------ stability facet branches

    private void appendStabilityFacetBranches(StringBuilder query, CatalogQuery filter) {
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

    private String resourceStabilityFacetBranch(CatalogQuery filter) {
        String v = v(filter, "r");
        CypherQuery q = startQuery(filter, "Resource", "r");
        appendDeprecatedWhere(q, v, filter);
        appendScopeWhere(q, v, filter);
        appendSingletonWhere(q, v, filter);
        appendDeclaresCapWhere(q, v, filter);
        appendDefinesAttrGroupsWhere(q, v, filter);
        return q.returning("toLower(" + v + ".stability) AS stability").toString();
    }

    private String attributeStabilityFacetBranch(CatalogQuery filter) {
        String v = v(filter, "a");
        CypherQuery q = startRelQuery(filter, "Attribute", "a", "<-[:HAS_ATTRIBUTE]-(r:Resource)");
        appendDeprecatedWhere(q, v, filter);
        appendScopeWhere(q, "r", filter);
        appendAttributeFilters(q, filter, v);
        return q.returning("toLower(" + v + ".stability) AS stability").toString();
    }

    private String operationStabilityFacetBranch(CatalogQuery filter) {
        String v = v(filter, "o");
        CypherQuery q = startRelQuery(filter, "Operation", "o", "<-[:PROVIDES]-(r:Resource)");
        appendDeprecatedWhere(q, v, filter);
        appendScopeWhere(q, "r", filter);
        appendOperationFilters(q, v, filter);
        return q.returning("toLower(" + v + ".stability) AS stability").toString();
    }

    private String capabilityStabilityFacetBranch(CatalogQuery filter) {
        CypherQuery q = CypherQuery.create();
        if (filter.hasDeprecatedFilter() && Boolean.TRUE.equals(filter.deprecated())) {
            return q.match("(c:Capability)").where("false").returning("toLower(c.stability) AS stability").toString();
        }
        if (filter.hasSearchQuery()) {
            q.match("(c:Capability)\n");
            q.where("ALL(part IN $capabilityTerms WHERE toLower(replace(c.name, '-', '')) CONTAINS part)");
            if (filter.hasScopeFilter()) {
                q.with("c");
                q.newLine().match("(c)<-[:DECLARES_CAPABILITY]-(r:Resource)").where("r.address STARTS WITH $scope");
            }
        } else if (filter.hasScopeFilter()) {
            q.match("(c:Capability)<-[:DECLARES_CAPABILITY]-(r:Resource)").where("r.address STARTS WITH $scope");
        } else {
            q.match("(c:Capability)");
        }
        return q.returning("toLower(c.stability) AS stability").toString();
    }

    // ------------------------------------------------------ deprecated facet branches

    private void appendDeprecatedFacetBranches(StringBuilder query, CatalogQuery filter) {
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

    private String resourceDeprecatedFacetBranch(CatalogQuery filter) {
        String v = v(filter, "r");
        CypherQuery q = startQuery(filter, "Resource", "r");
        appendStabilityWhere(q, v, filter);
        appendScopeWhere(q, v, filter);
        appendSingletonWhere(q, v, filter);
        appendDeclaresCapWhere(q, v, filter);
        appendDefinesAttrGroupsWhere(q, v, filter);
        return q.returning("EXISTS { (" + v + ")-[:DEPRECATED_SINCE]->() } AS deprecated").toString();
    }

    private String attributeDeprecatedFacetBranch(CatalogQuery filter) {
        String v = v(filter, "a");
        CypherQuery q = startRelQuery(filter, "Attribute", "a", "<-[:HAS_ATTRIBUTE]-(r:Resource)");
        appendStabilityWhere(q, v, filter);
        appendScopeWhere(q, "r", filter);
        appendAttributeFilters(q, filter, v);
        return q.returning("EXISTS { (" + v + ")-[:DEPRECATED_SINCE]->() } AS deprecated").toString();
    }

    private String operationDeprecatedFacetBranch(CatalogQuery filter) {
        String v = v(filter, "o");
        CypherQuery q = startRelQuery(filter, "Operation", "o", "<-[:PROVIDES]-(r:Resource)");
        appendStabilityWhere(q, v, filter);
        appendScopeWhere(q, "r", filter);
        appendOperationFilters(q, v, filter);
        return q.returning("EXISTS { (" + v + ")-[:DEPRECATED_SINCE]->() } AS deprecated").toString();
    }

    private String capabilityDeprecatedFacetBranch(CatalogQuery filter) {
        CypherQuery q = CypherQuery.create();
        if (filter.hasSearchQuery()) {
            q.match("(c:Capability)\n");
            q.where("ALL(part IN $capabilityTerms WHERE toLower(replace(c.name, '-', '')) CONTAINS part)");
            appendStabilityWhere(q, "c", filter);
            if (filter.hasScopeFilter()) {
                q.with("c");
                q.newLine().match("(c)<-[:DECLARES_CAPABILITY]-(r:Resource)").where("r.address STARTS WITH $scope");
            }
        } else if (filter.hasScopeFilter()) {
            q.match("(c:Capability)<-[:DECLARES_CAPABILITY]-(r:Resource)").where("r.address STARTS WITH $scope");
            appendStabilityWhere(q, "c", filter);
        } else {
            q.match("(c:Capability)");
            q.whereIf(filter.hasStabilityFilter(), "toLower(c.stability) IN $stabilities");
        }
        return q.returning("false AS deprecated").toString();
    }

    // ------------------------------------------------------ resource-specific facet counts

    private Map<String, Long> singletonFacetCounts(CatalogQuery filter) {
        String v = v(filter, "r");
        CypherQuery q = startQuery(filter, "Resource", "r");
        appendStabilityWhere(q, v, filter);
        appendDeprecatedWhere(q, v, filter);
        appendScopeWhere(q, v, filter);
        appendDeclaresCapWhere(q, v, filter);
        appendDefinesAttrGroupsWhere(q, v, filter);
        q.returning("CASE WHEN coalesce(" + v + ".singleton, false) THEN 'true' ELSE 'false' END AS value, count(*) AS count");

        Map<String, Object> params = buildFacetParams(filter);
        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(q.toString(), params).list().forEach(record ->
                    counts.put(record.get("value").asString(), record.get("count").asLong()));
            counts.putIfAbsent("true", 0L);
            counts.putIfAbsent("false", 0L);
            return counts;
        }
    }

    private Map<String, Long> declaresCapFacetCounts(CatalogQuery filter) {
        String v = v(filter, "r");
        CypherQuery q = startQuery(filter, "Resource", "r");
        appendStabilityWhere(q, v, filter);
        appendDeprecatedWhere(q, v, filter);
        appendScopeWhere(q, v, filter);
        appendSingletonWhere(q, v, filter);
        appendDefinesAttrGroupsWhere(q, v, filter);
        q.returning("CASE WHEN EXISTS { (" + v + ")-[:DECLARES_CAPABILITY]->() } THEN 'true' ELSE 'false' END AS value, count(*) AS count");

        Map<String, Object> params = buildFacetParams(filter);
        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(q.toString(), params).list().forEach(record ->
                    counts.put(record.get("value").asString(), record.get("count").asLong()));
            counts.putIfAbsent("true", 0L);
            counts.putIfAbsent("false", 0L);
            return counts;
        }
    }

    private Map<String, Long> definesAttrGroupsFacetCounts(CatalogQuery filter) {
        String v = v(filter, "r");
        CypherQuery q = startQuery(filter, "Resource", "r");
        appendStabilityWhere(q, v, filter);
        appendDeprecatedWhere(q, v, filter);
        appendScopeWhere(q, v, filter);
        appendSingletonWhere(q, v, filter);
        appendDeclaresCapWhere(q, v, filter);
        q.returning("CASE WHEN EXISTS { (" + v + ")-[:HAS_ATTRIBUTE]->(a:Attribute) WHERE a.`attribute-group` IS NOT NULL } THEN 'true' ELSE 'false' END AS value, count(*) AS count");

        Map<String, Object> params = buildFacetParams(filter);
        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(q.toString(), params).list().forEach(record ->
                    counts.put(record.get("value").asString(), record.get("count").asLong()));
            counts.putIfAbsent("true", 0L);
            counts.putIfAbsent("false", 0L);
            return counts;
        }
    }

    // ------------------------------------------------------ relationship-based attribute facet counts

    private Map<String, Long> dataTypeFacetCounts(CatalogQuery filter) {
        String v = v(filter, "a");
        CypherQuery q = startRelQuery(filter, "Attribute", "a", "<-[:HAS_ATTRIBUTE]-(r:Resource)");
        appendStabilityWhere(q, v, filter);
        appendDeprecatedWhere(q, v, filter);
        appendScopeWhere(q, "r", filter);
        appendAttributeFiltersExcluding(q, filter, "dataType", v);
        q.with(dataTypeCaseExpression(v) + " AS value");
        q.where("value IS NOT NULL");
        q.returning("value, count(*) AS count");

        Map<String, Object> params = buildFacetParams(filter);
        addAttributeFilterParamsExcluding(params, filter, "dataTypes");

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(q.toString(), params).list().forEach(record ->
                    counts.put(record.get("value").asString(), record.get("count").asLong()));
            counts.putIfAbsent("string", 0L);
            counts.putIfAbsent("int", 0L);
            counts.putIfAbsent("long", 0L);
            counts.putIfAbsent("boolean", 0L);
            counts.putIfAbsent("double", 0L);
            counts.putIfAbsent("big-decimal", 0L);
            counts.putIfAbsent("bytes", 0L);
            counts.putIfAbsent("property", 0L);
            counts.putIfAbsent("list-simple", 0L);
            counts.putIfAbsent("list-object", 0L);
            counts.putIfAbsent("map", 0L);
            counts.putIfAbsent("object", 0L);
            return counts;
        }
    }

    private Map<String, Long> sensitiveFacetCounts(CatalogQuery filter) {
        String v = v(filter, "a");
        CypherQuery q = startRelQuery(filter, "Attribute", "a", "<-[:HAS_ATTRIBUTE]-(r:Resource)");
        appendStabilityWhere(q, v, filter);
        appendDeprecatedWhere(q, v, filter);
        appendScopeWhere(q, "r", filter);
        appendAttributeFiltersExcluding(q, filter, "sensitive", v);
        q.returning("CASE WHEN EXISTS { (" + v
                + ")-[:IS_SENSITIVE]->() } THEN 'true' ELSE 'false' END AS value, count(*) AS count");

        Map<String, Object> params = buildFacetParams(filter);
        addAttributeFilterParamsExcluding(params, filter, null);

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(q.toString(), params).list().forEach(record ->
                    counts.put(record.get("value").asString(), record.get("count").asLong()));
            counts.putIfAbsent("true", 0L);
            counts.putIfAbsent("false", 0L);
            return counts;
        }
    }

    private Map<String, Long> hasRequiresFacetCounts(CatalogQuery filter) {
        return relationshipFacetCounts(filter, "hasRequires", "REQUIRES");
    }

    private Map<String, Long> hasAlternativesFacetCounts(CatalogQuery filter) {
        return relationshipFacetCounts(filter, "hasAlternatives", "ALTERNATIVE");
    }

    private Map<String, Long> relationshipFacetCounts(CatalogQuery filter, String facetName, String relType) {
        String v = v(filter, "a");
        CypherQuery q = startRelQuery(filter, "Attribute", "a", "<-[:HAS_ATTRIBUTE]-(r:Resource)");
        appendStabilityWhere(q, v, filter);
        appendDeprecatedWhere(q, v, filter);
        appendScopeWhere(q, "r", filter);
        appendAttributeFiltersExcluding(q, filter, facetName, v);
        q.returning("CASE WHEN EXISTS { (" + v
                + ")-[:" + relType + "]->() } THEN 'true' ELSE 'false' END AS value, count(*) AS count");

        Map<String, Object> params = buildFacetParams(filter);
        addAttributeFilterParamsExcluding(params, filter, null);

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(q.toString(), params).list().forEach(record ->
                    counts.put(record.get("value").asString(), record.get("count").asLong()));
            counts.putIfAbsent("true", 0L);
            counts.putIfAbsent("false", 0L);
            return counts;
        }
    }

    private Map<String, Long> refsCapFacetCounts(CatalogQuery filter) {
        String v = v(filter, "a");
        CypherQuery q = startRelQuery(filter, "Attribute", "a", "<-[:HAS_ATTRIBUTE]-(r:Resource)");
        appendStabilityWhere(q, v, filter);
        appendDeprecatedWhere(q, v, filter);
        appendScopeWhere(q, "r", filter);
        appendAttributeFiltersExcluding(q, filter, "refsCap", v);
        q.returning("CASE WHEN EXISTS { (" + v
                + ")-[:REFERENCES_CAPABILITY]->() } THEN 'true' ELSE 'false' END AS value, count(*) AS count");

        Map<String, Object> params = buildFacetParams(filter);
        addAttributeFilterParamsExcluding(params, filter, null);

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(q.toString(), params).list().forEach(record ->
                    counts.put(record.get("value").asString(), record.get("count").asLong()));
            counts.putIfAbsent("true", 0L);
            counts.putIfAbsent("false", 0L);
            return counts;
        }
    }

    // ------------------------------------------------------ operation facet counts

    private Map<String, Long> booleanOperationFacetCounts(CatalogQuery filter, String facetName, String propertyName) {
        String v = v(filter, "o");
        CypherQuery q = startRelQuery(filter, "Operation", "o", "<-[:PROVIDES]-(r:Resource)");
        appendStabilityWhere(q, v, filter);
        appendDeprecatedWhere(q, v, filter);
        appendScopeWhere(q, "r", filter);
        appendOperationFiltersExcluding(q, v, filter, facetName);
        q.returning("coalesce(" + v + "." + propertyName + ", false) AS value, count(*) AS count");

        Map<String, Object> params = buildFacetParams(filter);

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(q.toString(), params).list().forEach(record -> {
                boolean value = record.get("value").asBoolean();
                counts.put(value ? "true" : "false", record.get("count").asLong());
            });
            counts.putIfAbsent("true", 0L);
            counts.putIfAbsent("false", 0L);
            return counts;
        }
    }

    private Map<String, Long> hasParamsFacetCounts(CatalogQuery filter) {
        String v = v(filter, "o");
        CypherQuery q = startRelQuery(filter, "Operation", "o", "<-[:PROVIDES]-(r:Resource)");
        appendStabilityWhere(q, v, filter);
        appendDeprecatedWhere(q, v, filter);
        appendScopeWhere(q, "r", filter);
        appendOperationFiltersExcluding(q, v, filter, "hasParams");
        q.returning("EXISTS { (" + v + ")-[:ACCEPTS]->() } AS value, count(*) AS count");

        Map<String, Object> params = buildFacetParams(filter);

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(q.toString(), params).list().forEach(record -> {
                boolean value = record.get("value").asBoolean();
                counts.put(value ? "true" : "false", record.get("count").asLong());
            });
            counts.putIfAbsent("true", 0L);
            counts.putIfAbsent("false", 0L);
            return counts;
        }
    }

    private Map<String, Long> hasReturnValueFacetCounts(CatalogQuery filter) {
        String v = v(filter, "o");
        CypherQuery q = startRelQuery(filter, "Operation", "o", "<-[:PROVIDES]-(r:Resource)");
        appendStabilityWhere(q, v, filter);
        appendDeprecatedWhere(q, v, filter);
        appendScopeWhere(q, "r", filter);
        appendOperationFiltersExcluding(q, v, filter, "hasReturnValue");
        q.returning(v + ".`return-value` IS NOT NULL AS value, count(*) AS count");

        Map<String, Object> params = buildFacetParams(filter);

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(q.toString(), params).list().forEach(record -> {
                boolean value = record.get("value").asBoolean();
                counts.put(value ? "true" : "false", record.get("count").asLong());
            });
            counts.putIfAbsent("true", 0L);
            counts.putIfAbsent("false", 0L);
            return counts;
        }
    }

    // ------------------------------------------------------ params and mapping

    Map<String, Object> buildParams(CatalogQuery filter) {
        Map<String, Object> params = new HashMap<>();
        params.put("limit", filter.limit());
        params.put("offset", filter.offset());
        addFilterParams(params, filter);
        addAttributeFilterParams(params, filter);
        return params;
    }

    private Map<String, Object> buildFacetParams(CatalogQuery filter) {
        Map<String, Object> params = new HashMap<>();
        addFilterParams(params, filter);
        return params;
    }

    private void addFilterParams(Map<String, Object> params, CatalogQuery filter) {
        if (filter.hasStabilityFilter()) {
            params.put("stabilities", filter.stabilities());
        }
        if (filter.hasSearchQuery()) {
            params.put("term", LuceneUtils.escapeAndWildcard(filter.q()));
            params.put("capabilityTerms", LuceneUtils.capabilityTerms(filter.q()));
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

    private void addAttributeFilterParams(Map<String, Object> params, CatalogQuery filter) {
        if (filter.hasDataTypeFilter()) {
            List<String> scalarTypes = filter.dataTypes().stream()
                    .filter(t -> !REFINED_DATA_TYPES.contains(t))
                    .toList();
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
            CatalogQuery filter, String excludeParam) {
        if (filter.hasDataTypeFilter() && !"dataTypes".equals(excludeParam)) {
            List<String> scalarTypes = filter.dataTypes().stream()
                    .filter(t -> !REFINED_DATA_TYPES.contains(t))
                    .toList();
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

    private Set<String> applicableFacets(CatalogQuery filter) {
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

    private static CatalogResult toCatalogResult(Record record, CatalogQuery filter) {
        String type = record.get("type").asString();
        Properties properties = null;
        if ("resource".equals(type) && filter.includesType(CatalogType.RESOURCE)) {
            var capsValue = record.get("capabilities");
            List<String> capabilities = capsValue.isNull()
                    ? List.of()
                    : capsValue.asList(org.neo4j.driver.Value::asString);
            properties = new ResourceProperties(
                    record.get("singleton").asBoolean(false),
                    capabilities,
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
                        asString(propsMap.get("unit")),
                        asString(propsMap.get("capabilityReference")));
            }
        } else if ("operation".equals(type) && filter.includesType(CatalogType.OPERATION)) {
            properties = new OperationProperties(
                    record.get("readOnly").asBoolean(false),
                    record.get("runtimeOnly").asBoolean(false),
                    record.get("isGlobal").asBoolean(false),
                    record.get("hasParams").asBoolean(false),
                    record.get("hasReturnValue").asBoolean(false));
        } else if ("capability".equals(type) && filter.includesType(CatalogType.CAPABILITY)) {
            var drValue = record.get("declaringResources");
            List<String> declaringResources = drValue.isNull()
                    ? List.of()
                    : drValue.asList(org.neo4j.driver.Value::asString);
            properties = new CapabilityProperties(declaringResources);
        }
        return new CatalogResult(
                type,
                record.get("name").asString(null),
                record.get("description").asString(null),
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

    private static String dataTypeCaseExpression(String nodeVar) {
        return "CASE" +
                " WHEN " + nodeVar + ".type = 'LIST' AND " + nodeVar + ".`value-type` = 'OBJECT' THEN 'list-object'" +
                " WHEN " + nodeVar + ".type = 'LIST' THEN 'list-simple'" +
                " WHEN " + nodeVar + ".type = 'OBJECT' AND EXISTS { (" + nodeVar + ")-[:CONSISTS_OF]->() } THEN 'object'" +
                " WHEN " + nodeVar + ".type = 'OBJECT' AND (" + nodeVar + ".`value-type` = 'OBJECT' OR " + nodeVar + ".`value-type` IS NULL) THEN 'object'" +
                " WHEN " + nodeVar + ".type = 'OBJECT' THEN 'map'" +
                " ELSE toLower(" + nodeVar + ".type)" +
                " END";
    }
}
