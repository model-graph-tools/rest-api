package org.wildfly.modelgraph.api.catalog;

import static org.wildfly.modelgraph.api.catalog.CatalogRepository.addAttributeFilterParams;
import static org.wildfly.modelgraph.api.catalog.CatalogRepository.addAttributeFilterParamsExcluding;
import static org.wildfly.modelgraph.api.catalog.CatalogRepository.appendAttributeFilters;
import static org.wildfly.modelgraph.api.catalog.CatalogRepository.appendAttributeFiltersExcluding;
import static org.wildfly.modelgraph.api.catalog.CatalogRepository.appendCapabilityMatchWhere;
import static org.wildfly.modelgraph.api.catalog.CatalogRepository.appendDeclaresCapWhere;
import static org.wildfly.modelgraph.api.catalog.CatalogRepository.appendDefinesAttrGroupsWhere;
import static org.wildfly.modelgraph.api.catalog.CatalogRepository.appendDeprecatedWhere;
import static org.wildfly.modelgraph.api.catalog.CatalogRepository.appendOperationFilters;
import static org.wildfly.modelgraph.api.catalog.CatalogRepository.appendOperationFiltersExcluding;
import static org.wildfly.modelgraph.api.catalog.CatalogRepository.appendScopeWhere;
import static org.wildfly.modelgraph.api.catalog.CatalogRepository.appendSingletonWhere;
import static org.wildfly.modelgraph.api.catalog.CatalogRepository.appendStabilityWhere;
import static org.wildfly.modelgraph.api.catalog.CatalogRepository.buildFacetParams;
import static org.wildfly.modelgraph.api.catalog.CatalogRepository.dataTypeCaseExpression;
import static org.wildfly.modelgraph.api.catalog.CatalogRepository.startQuery;
import static org.wildfly.modelgraph.api.catalog.CatalogRepository.startRelQuery;
import static org.wildfly.modelgraph.api.catalog.CatalogRepository.v;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.neo4j.driver.Driver;
import org.wildfly.modelgraph.api.CypherQuery;

/** Computes per-facet counts for the catalog endpoint. */
@ApplicationScoped
public class CatalogFacetRepository {

    @Inject
    Driver driver;

    // ------------------------------------------------------ facet counts

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

    // ------------------------------------------------------ common facet counts

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

    // ------------------------------------------------------ resource-specific facet counts

    private Map<String, Long> singletonFacetCounts(CatalogQuery filter) {
        String var = v(filter, "r");
        CypherQuery q = startQuery(filter, "Resource", "r");
        appendStabilityWhere(q, var, filter);
        appendDeprecatedWhere(q, var, filter);
        appendScopeWhere(q, var, filter);
        appendDeclaresCapWhere(q, var, filter);
        appendDefinesAttrGroupsWhere(q, var, filter);
        q.returning("CASE WHEN coalesce(" + var + ".singleton, false) THEN 'true' ELSE 'false' END AS value, count(*) AS count");

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
        String var = v(filter, "r");
        CypherQuery q = startQuery(filter, "Resource", "r");
        appendStabilityWhere(q, var, filter);
        appendDeprecatedWhere(q, var, filter);
        appendScopeWhere(q, var, filter);
        appendSingletonWhere(q, var, filter);
        appendDefinesAttrGroupsWhere(q, var, filter);
        q.returning("CASE WHEN EXISTS { (" + var + ")-[:DECLARES_CAPABILITY]->() } THEN 'true' ELSE 'false' END AS value, count(*) AS count");

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
        String var = v(filter, "r");
        CypherQuery q = startQuery(filter, "Resource", "r");
        appendStabilityWhere(q, var, filter);
        appendDeprecatedWhere(q, var, filter);
        appendScopeWhere(q, var, filter);
        appendSingletonWhere(q, var, filter);
        appendDeclaresCapWhere(q, var, filter);
        q.returning("CASE WHEN EXISTS { (" + var + ")-[:HAS_ATTRIBUTE]->(a:Attribute) WHERE a.`attribute-group` IS NOT NULL } THEN 'true' ELSE 'false' END AS value, count(*) AS count");

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

    // ------------------------------------------------------ attribute facet counts

    private Map<String, Long> multiValueAttributeFacetCounts(CatalogQuery filter,
            String facetName, String cypherExpressionTemplate, String paramName) {
        String var = v(filter, "a");
        CypherQuery q = startRelQuery(filter, "Attribute", "a", "<-[:HAS_ATTRIBUTE]-(r:Resource)");
        appendStabilityWhere(q, var, filter);
        appendDeprecatedWhere(q, var, filter);
        appendScopeWhere(q, "r", filter);
        appendAttributeFiltersExcluding(q, filter, facetName, var);
        q.with(cypherExpressionTemplate.formatted(var) + " AS value");
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
        String var = v(filter, "a");
        CypherQuery q = startRelQuery(filter, "Attribute", "a", "<-[:HAS_ATTRIBUTE]-(r:Resource)");
        appendStabilityWhere(q, var, filter);
        appendDeprecatedWhere(q, var, filter);
        appendScopeWhere(q, "r", filter);
        appendAttributeFiltersExcluding(q, filter, facetName, var);
        String expr = cypherExpressionTemplate.formatted(var);
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
        String var = v(filter, "a");
        CypherQuery q = startRelQuery(filter, "Attribute", "a", "<-[:HAS_ATTRIBUTE]-(r:Resource)");
        appendStabilityWhere(q, var, filter);
        appendDeprecatedWhere(q, var, filter);
        appendScopeWhere(q, "r", filter);
        appendAttributeFiltersExcluding(q, filter, "hasAllowed", var);
        q.returning("CASE WHEN " + var + ".`allowed` IS NOT NULL THEN 'true' ELSE 'false' END AS value, count(*) AS count");

        Map<String, Object> params = buildFacetParams(filter);
        addAttributeFilterParamsExcluding(params, filter, null);

        try (var session = driver.session()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            session.run(q.toString(), params).list().forEach(record ->
                    counts.put(record.get("value").asString(), record.get("count").asLong()));
            return counts;
        }
    }

    private Map<String, Long> dataTypeFacetCounts(CatalogQuery filter) {
        String var = v(filter, "a");
        CypherQuery q = startRelQuery(filter, "Attribute", "a", "<-[:HAS_ATTRIBUTE]-(r:Resource)");
        appendStabilityWhere(q, var, filter);
        appendDeprecatedWhere(q, var, filter);
        appendScopeWhere(q, "r", filter);
        appendAttributeFiltersExcluding(q, filter, "dataType", var);
        q.with(dataTypeCaseExpression(var) + " AS value");
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
        String var = v(filter, "a");
        CypherQuery q = startRelQuery(filter, "Attribute", "a", "<-[:HAS_ATTRIBUTE]-(r:Resource)");
        appendStabilityWhere(q, var, filter);
        appendDeprecatedWhere(q, var, filter);
        appendScopeWhere(q, "r", filter);
        appendAttributeFiltersExcluding(q, filter, "sensitive", var);
        q.returning("CASE WHEN EXISTS { (" + var
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
        String var = v(filter, "a");
        CypherQuery q = startRelQuery(filter, "Attribute", "a", "<-[:HAS_ATTRIBUTE]-(r:Resource)");
        appendStabilityWhere(q, var, filter);
        appendDeprecatedWhere(q, var, filter);
        appendScopeWhere(q, "r", filter);
        appendAttributeFiltersExcluding(q, filter, facetName, var);
        q.returning("CASE WHEN EXISTS { (" + var
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
        String var = v(filter, "a");
        CypherQuery q = startRelQuery(filter, "Attribute", "a", "<-[:HAS_ATTRIBUTE]-(r:Resource)");
        appendStabilityWhere(q, var, filter);
        appendDeprecatedWhere(q, var, filter);
        appendScopeWhere(q, "r", filter);
        appendAttributeFiltersExcluding(q, filter, "refsCap", var);
        q.returning("CASE WHEN EXISTS { (" + var
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
        String var = v(filter, "o");
        CypherQuery q = startRelQuery(filter, "Operation", "o", "<-[:PROVIDES]-(r:Resource)");
        appendStabilityWhere(q, var, filter);
        appendDeprecatedWhere(q, var, filter);
        appendScopeWhere(q, "r", filter);
        appendOperationFiltersExcluding(q, var, filter, facetName);
        q.returning("coalesce(" + var + "." + propertyName + ", false) AS value, count(*) AS count");

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
        String var = v(filter, "o");
        CypherQuery q = startRelQuery(filter, "Operation", "o", "<-[:PROVIDES]-(r:Resource)");
        appendStabilityWhere(q, var, filter);
        appendDeprecatedWhere(q, var, filter);
        appendScopeWhere(q, "r", filter);
        appendOperationFiltersExcluding(q, var, filter, "hasParams");
        q.returning("EXISTS { (" + var + ")-[:ACCEPTS]->() } AS value, count(*) AS count");

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
        String var = v(filter, "o");
        CypherQuery q = startRelQuery(filter, "Operation", "o", "<-[:PROVIDES]-(r:Resource)");
        appendStabilityWhere(q, var, filter);
        appendDeprecatedWhere(q, var, filter);
        appendScopeWhere(q, "r", filter);
        appendOperationFiltersExcluding(q, var, filter, "hasReturnValue");
        q.returning(var + ".`return-value` IS NOT NULL AS value, count(*) AS count");

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
        String var = v(filter, "r");
        CypherQuery q = startQuery(filter, "Resource", "r");
        appendStabilityWhere(q, var, filter);
        appendDeprecatedWhere(q, var, filter);
        appendScopeWhere(q, var, filter);
        appendSingletonWhere(q, var, filter);
        appendDeclaresCapWhere(q, var, filter);
        appendDefinesAttrGroupsWhere(q, var, filter);
        return q.returning("'resource' AS type").toString();
    }

    private String attributeTypeFacetBranch(CatalogQuery filter) {
        String var = v(filter, "a");
        CypherQuery q = startRelQuery(filter, "Attribute", "a", "<-[:HAS_ATTRIBUTE]-(r:Resource)");
        appendStabilityWhere(q, var, filter);
        appendDeprecatedWhere(q, var, filter);
        appendScopeWhere(q, "r", filter);
        appendAttributeFilters(q, filter, var);
        return q.returning("'attribute' AS type").toString();
    }

    private String operationTypeFacetBranch(CatalogQuery filter) {
        String var = v(filter, "o");
        CypherQuery q = startRelQuery(filter, "Operation", "o", "<-[:PROVIDES]-(r:Resource)");
        appendStabilityWhere(q, var, filter);
        appendDeprecatedWhere(q, var, filter);
        appendScopeWhere(q, "r", filter);
        appendOperationFilters(q, var, filter);
        return q.returning("'operation' AS type").toString();
    }

    private String capabilityTypeFacetBranch(CatalogQuery filter) {
        CypherQuery q = CypherQuery.create();
        if (filter.hasDeprecatedFilter() && Boolean.TRUE.equals(filter.deprecated())) {
            return q.match("(c:Capability)").where("false").returning("'capability' AS type").toString();
        }
        appendCapabilityMatchWhere(q, filter, true);
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
        if (branches.isEmpty()) {
            query.append("RETURN null AS stability\n");
        } else {
            query.append(String.join("\nUNION ALL\n", branches));
            query.append("\n");
        }
    }

    private String resourceStabilityFacetBranch(CatalogQuery filter) {
        String var = v(filter, "r");
        CypherQuery q = startQuery(filter, "Resource", "r");
        appendDeprecatedWhere(q, var, filter);
        appendScopeWhere(q, var, filter);
        appendSingletonWhere(q, var, filter);
        appendDeclaresCapWhere(q, var, filter);
        appendDefinesAttrGroupsWhere(q, var, filter);
        return q.returning("toLower(" + var + ".stability) AS stability").toString();
    }

    private String attributeStabilityFacetBranch(CatalogQuery filter) {
        String var = v(filter, "a");
        CypherQuery q = startRelQuery(filter, "Attribute", "a", "<-[:HAS_ATTRIBUTE]-(r:Resource)");
        appendDeprecatedWhere(q, var, filter);
        appendScopeWhere(q, "r", filter);
        appendAttributeFilters(q, filter, var);
        return q.returning("toLower(" + var + ".stability) AS stability").toString();
    }

    private String operationStabilityFacetBranch(CatalogQuery filter) {
        String var = v(filter, "o");
        CypherQuery q = startRelQuery(filter, "Operation", "o", "<-[:PROVIDES]-(r:Resource)");
        appendDeprecatedWhere(q, var, filter);
        appendScopeWhere(q, "r", filter);
        appendOperationFilters(q, var, filter);
        return q.returning("toLower(" + var + ".stability) AS stability").toString();
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
        String var = v(filter, "r");
        CypherQuery q = startQuery(filter, "Resource", "r");
        appendStabilityWhere(q, var, filter);
        appendScopeWhere(q, var, filter);
        appendSingletonWhere(q, var, filter);
        appendDeclaresCapWhere(q, var, filter);
        appendDefinesAttrGroupsWhere(q, var, filter);
        return q.returning("EXISTS { (" + var + ")-[:DEPRECATED_SINCE]->() } AS deprecated").toString();
    }

    private String attributeDeprecatedFacetBranch(CatalogQuery filter) {
        String var = v(filter, "a");
        CypherQuery q = startRelQuery(filter, "Attribute", "a", "<-[:HAS_ATTRIBUTE]-(r:Resource)");
        appendStabilityWhere(q, var, filter);
        appendScopeWhere(q, "r", filter);
        appendAttributeFilters(q, filter, var);
        return q.returning("EXISTS { (" + var + ")-[:DEPRECATED_SINCE]->() } AS deprecated").toString();
    }

    private String operationDeprecatedFacetBranch(CatalogQuery filter) {
        String var = v(filter, "o");
        CypherQuery q = startRelQuery(filter, "Operation", "o", "<-[:PROVIDES]-(r:Resource)");
        appendStabilityWhere(q, var, filter);
        appendScopeWhere(q, "r", filter);
        appendOperationFilters(q, var, filter);
        return q.returning("EXISTS { (" + var + ")-[:DEPRECATED_SINCE]->() } AS deprecated").toString();
    }

    private String capabilityDeprecatedFacetBranch(CatalogQuery filter) {
        CypherQuery q = CypherQuery.create();
        appendCapabilityMatchWhere(q, filter, true);
        return q.returning("false AS deprecated").toString();
    }

    // ------------------------------------------------------ internal

    private Set<String> applicableFacets(CatalogQuery filter) {
        if (!filter.hasTypeFilter()) {
            Set<String> all = new HashSet<>();
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
}
