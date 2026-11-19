package org.wildfly.modelgraph.api.catalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.wildfly.modelgraph.api.CypherQuery;
import org.wildfly.modelgraph.api.LuceneUtils;

/** Builds dynamic Cypher queries from catalog filter combinations and executes them against Neo4j. */
@ApplicationScoped
public class CatalogRepository {

    private static final Logger LOG = Logger.getLogger(CatalogRepository.class);
    private static final String SEARCH_INDEX = LuceneUtils.SEARCH_INDEX;
    private static final Set<String> SAFE_SORT_FIELDS = Set.of("name", "type", "resourceAddress", "stability");
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

        if ("type".equals(sort)) {
            query.append("ORDER BY type ").append(dir).append(", name ASC\n");
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
            query.append(" LIMIT 0\n");
        } else {
            query.append(String.join("\nUNION ALL\n", branches));
            query.append("\n");
        }
    }

    // ------------------------------------------------------ result branches

    private String resourceBranch(CatalogQuery filter, boolean includesOperation, boolean includesCapability) {
        String v = v(filter, "r");
        CypherQuery q = startQuery(filter, "Resource", "r");
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
        return q.toString();
    }

    private String attributeBranch(CatalogQuery filter, boolean includesResource, boolean includesOperation,
            boolean includesCapability) {
        String v = v(filter, "a");
        CypherQuery q = startRelQuery(filter, "Attribute", "a", "<-[:HAS_ATTRIBUTE]-(r:Resource)");
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
        return q.toString();
    }

    private String operationBranch(CatalogQuery filter, boolean includesResource, boolean includesCapability) {
        String v = v(filter, "o");
        CypherQuery q = startRelQuery(filter, "Operation", "o", "<-[:PROVIDES]-(r:Resource)");
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
        return q.toString();
    }

    private String capabilityBranch(CatalogQuery filter, boolean includesResource, boolean includesOperation) {
        CypherQuery q = CypherQuery.create();
        if (filter.hasDeprecatedFilter() && Boolean.TRUE.equals(filter.deprecated())) {
            q.match("(c:Capability)").where("false");
            q.returning("'capability' AS type, c.name AS name, null AS description, "
                    + "null AS resourceAddress, null AS stability, "
                    + "false AS deprecated, null AS properties");
            if (includesResource) {
                q.append(", null AS singleton, null AS capabilities, null AS definesAttrGroups");
            }
            if (includesOperation) {
                q.append(NULL_OP_PROPS);
            }
            q.append(", [] AS declaringResources");
            return q.toString();
        }

        appendCapabilityMatchWhere(q, filter, true);
        q.returning("'capability' AS type, c.name AS name, null AS description, "
                + "null AS resourceAddress, null AS stability, "
                + "false AS deprecated, null AS properties");
        if (includesResource) {
            q.append(", null AS singleton, null AS capabilities, null AS definesAttrGroups");
        }
        if (includesOperation) {
            q.append(NULL_OP_PROPS);
        }
        q.append(", [(c)<-[d:DECLARES_CAPABILITY]-(dr:Resource) | {address: dr.address, stability: d.stability}] AS declaringResources");
        return q.toString();
    }

    // ------------------------------------------------------ query start helpers

    static CypherQuery startQuery(CatalogQuery filter, String label, String var) {
        CypherQuery q = CypherQuery.create();
        if (filter.hasSearchQuery()) {
            q.fullTextSearch(SEARCH_INDEX, label);
        } else {
            q.match("(" + var + ":" + label + ")");
        }
        return q;
    }

    static CypherQuery startRelQuery(CatalogQuery filter, String label, String var, String relationship) {
        CypherQuery q = CypherQuery.create();
        if (filter.hasSearchQuery()) {
            q.fullTextSearch(SEARCH_INDEX, label);
            q.newLine().match("(node)" + relationship);
        } else {
            q.match("(" + var + ":" + label + ")" + relationship);
        }
        return q;
    }

    static String v(CatalogQuery filter, String defaultVar) {
        return filter.hasSearchQuery() ? "node" : defaultVar;
    }

    private static String opProps(String v) {
        return ", coalesce(" + v + ".`read-only`, false) AS readOnly, coalesce(" + v
                + ".`runtime-only`, false) AS runtimeOnly, coalesce(" + v
                + ".global, false) AS isGlobal, EXISTS { (" + v
                + ")-[:ACCEPTS]->() } AS hasParams, " + v + ".`return-value` IS NOT NULL AS hasReturnValue";
    }

    // ------------------------------------------------------ capability match/where helper

    static void appendCapabilityMatchWhere(CypherQuery q, CatalogQuery filter, boolean includeStability) {
        if (filter.hasSearchQuery()) {
            if (includeStability && filter.hasStabilityFilter()) {
                q.match("(c:Capability)<-[d:DECLARES_CAPABILITY]-(r:Resource)\n");
                q.where("ALL(part IN $capabilityTerms WHERE toLower(replace(c.name, '-', '')) CONTAINS part)");
                q.where("toLower(d.stability) IN $stabilities");
                if (filter.hasScopeFilter()) {
                    q.where("r.address STARTS WITH $scope");
                }
            } else {
                q.match("(c:Capability)\n");
                q.where("ALL(part IN $capabilityTerms WHERE toLower(replace(c.name, '-', '')) CONTAINS part)");
                if (filter.hasScopeFilter()) {
                    q.with("c");
                    q.newLine().match("(c)<-[:DECLARES_CAPABILITY]-(r:Resource)").where("r.address STARTS WITH $scope");
                }
            }
        } else if (filter.hasScopeFilter()) {
            if (includeStability && filter.hasStabilityFilter()) {
                q.match("(c:Capability)<-[d:DECLARES_CAPABILITY]-(r:Resource)").where("r.address STARTS WITH $scope");
                q.where("toLower(d.stability) IN $stabilities");
            } else {
                q.match("(c:Capability)<-[:DECLARES_CAPABILITY]-(r:Resource)").where("r.address STARTS WITH $scope");
            }
        } else {
            if (includeStability && filter.hasStabilityFilter()) {
                q.match("(c:Capability)<-[d:DECLARES_CAPABILITY]-()");
                q.where("toLower(d.stability) IN $stabilities");
            } else {
                q.match("(c:Capability)");
            }
        }
    }

    // ------------------------------------------------------ where clause helpers

    static void appendStabilityWhere(CypherQuery q, String nodeVar, CatalogQuery filter) {
        q.whereIf(filter.hasStabilityFilter(), "toLower(" + nodeVar + ".stability) IN $stabilities");
    }

    static void appendDeprecatedWhere(CypherQuery q, String nodeVar, CatalogQuery filter) {
        if (filter.hasDeprecatedFilter()) {
            if (Boolean.TRUE.equals(filter.deprecated())) {
                q.where("EXISTS { (" + nodeVar + ")-[:DEPRECATED_SINCE]->() }");
            } else {
                q.where("NOT EXISTS { (" + nodeVar + ")-[:DEPRECATED_SINCE]->() }");
            }
        }
    }

    static void appendScopeWhere(CypherQuery q, String nodeVar, CatalogQuery filter) {
        q.whereIf(filter.hasScopeFilter(), nodeVar + ".address STARTS WITH $scope");
    }

    static void appendSingletonWhere(CypherQuery q, String nodeVar, CatalogQuery filter) {
        q.whereIf(filter.hasSingletonFilter(), "coalesce(" + nodeVar + ".singleton, false) = $singleton");
    }

    static void appendDeclaresCapWhere(CypherQuery q, String nodeVar, CatalogQuery filter) {
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

    static void appendDefinesAttrGroupsWhere(CypherQuery q, String nodeVar, CatalogQuery filter) {
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

    static void appendDataTypeFilter(CypherQuery q, CatalogQuery filter, String nodeVar) {
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

    static void appendSensitiveWhere(CypherQuery q, String nodeVar, CatalogQuery filter) {
        if (filter.hasSensitiveFilter()) {
            if (filter.sensitive()) {
                q.where("EXISTS { (" + nodeVar + ")-[:IS_SENSITIVE]->() }");
            } else {
                q.where("NOT EXISTS { (" + nodeVar + ")-[:IS_SENSITIVE]->() }");
            }
        }
    }

    static void appendHasRequiresWhere(CypherQuery q, String nodeVar, CatalogQuery filter) {
        if (filter.hasHasRequiresFilter()) {
            if (filter.hasRequires()) {
                q.where("EXISTS { (" + nodeVar + ")-[:REQUIRES]->() }");
            } else {
                q.where("NOT EXISTS { (" + nodeVar + ")-[:REQUIRES]->() }");
            }
        }
    }

    static void appendHasAlternativesWhere(CypherQuery q, String nodeVar, CatalogQuery filter) {
        if (filter.hasHasAlternativesFilter()) {
            if (filter.hasAlternatives()) {
                q.where("EXISTS { (" + nodeVar + ")-[:ALTERNATIVE]->() }");
            } else {
                q.where("NOT EXISTS { (" + nodeVar + ")-[:ALTERNATIVE]->() }");
            }
        }
    }

    static void appendRefsCapWhere(CypherQuery q, String nodeVar, CatalogQuery filter) {
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

    static void appendAttributeFilters(CypherQuery q, CatalogQuery filter, String nodeVar) {
        appendAttributeFiltersExcluding(q, filter, null, nodeVar);
    }

    static void appendAttributeFiltersExcluding(CypherQuery q, CatalogQuery filter,
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

    static void appendOperationFilters(CypherQuery q, String nodeVar, CatalogQuery filter) {
        appendOperationFiltersExcluding(q, nodeVar, filter, null);
    }

    static void appendOperationFiltersExcluding(CypherQuery q, String nodeVar,
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

    // ------------------------------------------------------ params and mapping

    Map<String, Object> buildParams(CatalogQuery filter) {
        Map<String, Object> params = new HashMap<>();
        params.put("limit", filter.limit());
        params.put("offset", filter.offset());
        addFilterParams(params, filter);
        addAttributeFilterParams(params, filter);
        return params;
    }

    static Map<String, Object> buildFacetParams(CatalogQuery filter) {
        Map<String, Object> params = new HashMap<>();
        addFilterParams(params, filter);
        return params;
    }

    static void addFilterParams(Map<String, Object> params, CatalogQuery filter) {
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

    static void addAttributeFilterParams(Map<String, Object> params, CatalogQuery filter) {
        addAttributeFilterParamsExcluding(params, filter, null);
    }

    static void addAttributeFilterParamsExcluding(Map<String, Object> params,
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
            List<DeclaringResource> declaringResources = drValue.isNull()
                    ? List.of()
                    : drValue.asList(v -> new DeclaringResource(
                            v.get("address").asString(null),
                            v.get("stability").asString(null)));
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

    static String dataTypeCaseExpression(String nodeVar) {
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
