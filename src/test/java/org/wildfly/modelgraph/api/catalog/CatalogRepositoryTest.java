package org.wildfly.modelgraph.api.catalog;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogRepositoryTest {

    final CatalogRepository repository = new CatalogRepository();

    // ------------------------------------------------------ no filters

    @Test
    void noFiltersIncludesAllFourBranches() {
        String query = repository.buildResultQuery(queryWith(null, null, null, null));
        assertTrue(query.contains("'resource' AS type"), "should include resource branch");
        assertTrue(query.contains("'attribute' AS type"), "should include attribute branch");
        assertTrue(query.contains("'operation' AS type"), "should include operation branch");
        assertTrue(query.contains("'capability' AS type"), "should include capability branch");
        assertTrue(query.contains("UNION ALL"), "branches joined with UNION ALL");
    }

    @Test
    void noFiltersResultQueryHasPaginationAndOrderBy() {
        String query = repository.buildResultQuery(queryWith(null, null, null, null));
        assertTrue(query.contains("SKIP $offset LIMIT $limit"), "should have pagination");
        assertTrue(query.contains("ORDER BY"), "should have ORDER BY");
    }

    @Test
    void noFiltersTotalQueryReturnsCount() {
        String query = repository.buildTotalQuery(queryWith(null, null, null, null));
        assertTrue(query.contains("RETURN count(*) AS total"), "total query returns count");
        assertFalse(query.contains("SKIP"), "total query has no pagination");
        assertFalse(query.contains("ORDER BY"), "total query has no ORDER BY");
    }

    // ------------------------------------------------------ single type filter

    @Test
    void singleTypeFilterAttributeOnly() {
        String query = repository.buildResultQuery(queryWith(null, "attribute", null, null));
        assertTrue(query.contains("'attribute' AS type"), "should include attribute branch");
        assertFalse(query.contains("'resource' AS type"), "should exclude resource branch");
        assertFalse(query.contains("'operation' AS type"), "should exclude operation branch");
        assertFalse(query.contains("'capability' AS type"), "should exclude capability branch");
    }

    @Test
    void singleTypeFilterResourceOnly() {
        String query = repository.buildResultQuery(queryWith(null, "resource", null, null));
        assertTrue(query.contains("'resource' AS type"), "should include resource branch");
        assertFalse(query.contains("'attribute' AS type"), "should exclude attribute branch");
    }

    @Test
    void singleTypeFilterOperationOnly() {
        String query = repository.buildResultQuery(queryWith(null, "operation", null, null));
        assertTrue(query.contains("'operation' AS type"), "should include operation branch");
        assertFalse(query.contains("'attribute' AS type"), "should exclude attribute branch");
        assertFalse(query.contains("'resource' AS type"), "should exclude resource branch");
    }

    @Test
    void singleTypeFilterCapabilityOnly() {
        String query = repository.buildResultQuery(queryWith(null, "capability", null, null));
        assertTrue(query.contains("'capability' AS type"), "should include capability branch");
        assertFalse(query.contains("'attribute' AS type"), "should exclude attribute branch");
    }

    // ------------------------------------------------------ attribute filters suppress other types

    @Test
    void attributeFilterSuppressesNonAttributeTypes() {
        CatalogQuery filter = CatalogQuery.of(null, null, null, null, null,
                null, null, null,
                "string", null, null, null,
                null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null);
        String query = repository.buildResultQuery(filter);
        assertTrue(query.contains("'attribute' AS type"), "attribute branch present");
        assertFalse(query.contains("'operation' AS type"), "operation branch suppressed");
        assertFalse(query.contains("'resource' AS type"), "resource branch suppressed");
        assertFalse(query.contains("'capability' AS type"), "capability branch suppressed");
    }

    @Test
    void accessTypeFilterSuppressesNonAttributeTypes() {
        CatalogQuery filter = CatalogQuery.of(null, null, null, null, null,
                null, null, null,
                null, "read-write", null, null,
                null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null);
        String query = repository.buildResultQuery(filter);
        assertTrue(query.contains("'attribute' AS type"), "attribute branch present");
        assertFalse(query.contains("'resource' AS type"), "resource branch suppressed");
    }

    // ------------------------------------------------------ operation filters suppress other types

    @Test
    void operationFilterSuppressesNonOperationTypes() {
        CatalogQuery filter = CatalogQuery.of(null, null, null, null, null,
                null, null, null,
                null, null, null, null,
                null, null, null,
                null, null, null, null, null, null,
                true, null, null, null, null,
                null, null, null, null);
        String query = repository.buildResultQuery(filter);
        assertTrue(query.contains("'operation' AS type"), "operation branch present");
        assertFalse(query.contains("'attribute' AS type"), "attribute branch suppressed");
        assertFalse(query.contains("'resource' AS type"), "resource branch suppressed");
        assertFalse(query.contains("'capability' AS type"), "capability branch suppressed");
    }

    // ------------------------------------------------------ resource filters suppress other types

    @Test
    void resourceFilterSuppressesNonResourceTypes() {
        CatalogQuery filter = CatalogQuery.of(null, null, null, null, null,
                true, null, null,
                null, null, null, null,
                null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null);
        String query = repository.buildResultQuery(filter);
        assertTrue(query.contains("'resource' AS type"), "resource branch present");
        assertFalse(query.contains("'attribute' AS type"), "attribute branch suppressed");
        assertFalse(query.contains("'operation' AS type"), "operation branch suppressed");
        assertFalse(query.contains("'capability' AS type"), "capability branch suppressed");
    }

    // ------------------------------------------------------ search query

    @Test
    void searchQueryIncludesFulltextCall() {
        String query = repository.buildResultQuery(queryWith("pool", null, null, null));
        assertTrue(query.contains("db.index.fulltext.queryNodes"), "should use fulltext index");
        assertTrue(query.contains("score"), "should include score column");
    }

    @Test
    void searchQueryDefaultsSortToScore() {
        String query = repository.buildResultQuery(queryWith("pool", null, null, null));
        assertTrue(query.contains("ORDER BY score DESC"), "search defaults to score sort");
    }

    @Test
    void noSearchQueryOmitsFulltextCall() {
        String query = repository.buildResultQuery(queryWith(null, null, null, null));
        assertFalse(query.contains("db.index.fulltext.queryNodes"), "no fulltext without search query");
    }

    // ------------------------------------------------------ scope filter

    @Test
    void scopeFilterIncludesStartsWith() {
        CatalogQuery filter = CatalogQuery.of(null, null, null, null,
                "/subsystem=datasources",
                null, null, null,
                null, null, null, null,
                null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null);
        String query = repository.buildResultQuery(filter);
        assertTrue(query.contains("STARTS WITH $scope"), "should filter by scope");
    }

    // ------------------------------------------------------ deprecated filter

    @Test
    void deprecatedTrueIncludesDeprecatedSinceCheck() {
        String query = repository.buildResultQuery(queryWith(null, null, null, true));
        assertTrue(query.contains("DEPRECATED_SINCE"), "should check deprecated relationship");
    }

    @Test
    void deprecatedTrueCapabilityBranchReturnsFalse() {
        String query = repository.buildResultQuery(queryWith(null, null, null, true));
        assertTrue(query.contains("(c:Capability) WHERE false"), "capabilities excluded when deprecated=true");
    }

    @Test
    void deprecatedFalseIncludesNotDeprecatedCheck() {
        String query = repository.buildResultQuery(queryWith(null, null, null, false));
        assertTrue(query.contains("NOT EXISTS { (r)-[:DEPRECATED_SINCE]->() }"),
                "should check NOT deprecated for resources");
    }

    // ------------------------------------------------------ stability filter

    @Test
    void stabilityFilterIncludesStabilitiesParam() {
        String query = repository.buildResultQuery(queryWith(null, null, "default,community", null));
        assertTrue(query.contains("stability) IN $stabilities"), "should filter by stability");
    }

    // ------------------------------------------------------ buildParams

    @Test
    void buildParamsAlwaysIncludesLimitAndOffset() {
        Map<String, Object> params = repository.buildParams(queryWith(null, null, null, null));
        assertTrue(params.containsKey("limit"), "should have limit");
        assertTrue(params.containsKey("offset"), "should have offset");
        assertEquals(50, params.get("limit"), "default limit is 50");
        assertEquals(0, params.get("offset"), "default offset is 0");
    }

    @Test
    void buildParamsIncludesSearchTerms() {
        Map<String, Object> params = repository.buildParams(queryWith("pool", null, null, null));
        assertTrue(params.containsKey("term"), "should have term");
        assertTrue(params.containsKey("capabilityTerms"), "should have capabilityTerms");
        assertEquals("pool*", params.get("term"), "term should be escaped and wildcarded");
        assertEquals(List.of("pool"), params.get("capabilityTerms"), "capabilityTerms should be split");
    }

    @Test
    void buildParamsIncludesStabilities() {
        Map<String, Object> params = repository.buildParams(queryWith(null, null, "default,community", null));
        assertTrue(params.containsKey("stabilities"), "should have stabilities");
        assertEquals(List.of("default", "community"), params.get("stabilities"));
    }

    @Test
    void buildParamsIncludesScope() {
        CatalogQuery filter = CatalogQuery.of(null, null, null, null,
                "/subsystem=io",
                null, null, null,
                null, null, null, null,
                null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null);
        Map<String, Object> params = repository.buildParams(filter);
        assertEquals("/subsystem=io", params.get("scope"));
    }

    @Test
    void buildParamsIncludesAttributeFilters() {
        CatalogQuery filter = CatalogQuery.of(null, null, null, null, null,
                null, null, null,
                "string,int", "read-write", "configuration", null,
                true, false, null,
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null);
        Map<String, Object> params = repository.buildParams(filter);
        assertEquals(List.of("string", "int"), params.get("dataTypes"));
        assertEquals(List.of("read-write"), params.get("accessTypes"));
        assertEquals(List.of("configuration"), params.get("storageValues"));
        assertEquals(true, params.get("required"));
        assertEquals(false, params.get("nillable"));
    }

    @Test
    void buildParamsOmitsUnsetFilters() {
        Map<String, Object> params = repository.buildParams(queryWith(null, null, null, null));
        assertFalse(params.containsKey("term"), "no term without search query");
        assertFalse(params.containsKey("stabilities"), "no stabilities without filter");
        assertFalse(params.containsKey("scope"), "no scope without filter");
        assertFalse(params.containsKey("dataTypes"), "no dataTypes without filter");
    }

    @Test
    void buildParamsRefinedDataTypesExcluded() {
        CatalogQuery filter = CatalogQuery.of(null, null, null, null, null,
                null, null, null,
                "list-simple", null, null, null,
                null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null);
        Map<String, Object> params = repository.buildParams(filter);
        assertFalse(params.containsKey("dataTypes"),
                "list-simple-only dataType should not add dataTypes param");
    }

    @Test
    void buildParamsMixedRefinedAndScalarDataTypes() {
        CatalogQuery filter = CatalogQuery.of(null, null, null, null, null,
                null, null, null,
                "string,list-object,map", null, null, null,
                null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null);
        Map<String, Object> params = repository.buildParams(filter);
        assertEquals(List.of("string"), params.get("dataTypes"),
                "refined categories filtered out, only scalar types remain");
    }

    // ------------------------------------------------------ order by

    @Test
    void defaultSortIsNameAsc() {
        String query = repository.buildResultQuery(queryWith(null, null, null, null));
        assertTrue(query.contains("ORDER BY name ASC"), "default sort is name ASC");
    }

    @Test
    void resourceOnlyDefaultsSortToAddress() {
        String query = repository.buildResultQuery(queryWith(null, "resource", null, null));
        assertTrue(query.contains("ORDER BY CASE WHEN address IS NULL"), "resource-only defaults to address sort");
    }

    // ------------------------------------------------------ empty branches

    @Test
    void conflictingFiltersProduceEmptyResult() {
        CatalogQuery filter = CatalogQuery.of(null, "capability", null, null, null,
                null, null, null,
                "string", null, null, null,
                null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null);
        String query = repository.buildResultQuery(filter);
        assertTrue(query.contains("LIMIT 0"), "conflicting type+attribute filters produce empty branch");
    }

    // ------------------------------------------------------ security: defense-in-depth

    @Test
    void orderByOnlyContainsSafeSortFields() {
        // Verify that the ORDER BY clause only contains safe field names.
        // This is defense-in-depth — CatalogQuery's allowlist is the first line,
        // CatalogRepository's SAFE_SORT_FIELDS is the second.
        for (String validSort : List.of("name", "type", "stability")) {
            CatalogQuery filter = CatalogQuery.of(null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null,
                    validSort, null, null, null);
            String query = repository.buildResultQuery(filter);
            String orderByLine = extractOrderByLine(query);
            assertTrue(orderByLine.startsWith("ORDER BY " + validSort + " "),
                    "ORDER BY must use safe field '" + validSort + "', got: " + orderByLine);
        }
    }

    @Test
    void orderByAddressSortUsesNullSafeExpression() {
        CatalogQuery filter = CatalogQuery.of(null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                "address", null, null, null);
        String query = repository.buildResultQuery(filter);
        String orderByLine = extractOrderByLine(query);
        assertTrue(orderByLine.contains("CASE WHEN address IS NULL"), "address sort must handle nulls");
    }

    @Test
    void orderByWithSearchQueryUsesScoreSort() {
        CatalogQuery filter = CatalogQuery.of("pool", null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
        String query = repository.buildResultQuery(filter);
        assertTrue(query.contains("ORDER BY score DESC, name ASC"));
    }

    @Test
    void generatedQueryNeverContainsRawUserInput() {
        // Verify that the search term is never interpolated into the query —
        // it must only appear as a $term parameter reference
        CatalogQuery filter = CatalogQuery.of("malicious'; MATCH (n) DELETE n//", null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
        String query = repository.buildResultQuery(filter);
        assertFalse(query.contains("malicious"), "user search term must not be in query string");
        assertTrue(query.contains("$term"), "search term must be passed as parameter");
        assertTrue(query.contains("$capabilityTerms"), "capability terms must be passed as parameter");
    }

    // ------------------------------------------------------ helpers

    private static String extractOrderByLine(String query) {
        for (String line : query.split("\n")) {
            if (line.stripLeading().startsWith("ORDER BY")) {
                return line.strip();
            }
        }
        return "";
    }

    private static CatalogQuery queryWith(String q, String type, String stability, Boolean deprecated) {
        return CatalogQuery.of(q, type, stability, deprecated, null,
                null, null, null,
                null, null, null, null,
                null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null);
    }
}
