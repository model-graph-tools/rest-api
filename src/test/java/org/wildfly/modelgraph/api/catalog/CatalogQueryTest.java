package org.wildfly.modelgraph.api.catalog;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogQueryTest {

    // ------------------------------------------------------ helper

    private static CatalogQuery query() {
        return queryWith(null, null, null, null, null, null, null, null);
    }

    private static CatalogQuery queryWith(String q, String type, String sort, String order,
            Integer limit, Integer offset, String declaresCap, String refsCap) {
        return CatalogQuery.of(q, type, null, null, null,
                null, declaresCap, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, refsCap,
                null, null, null, null, null,
                sort, order, limit, offset);
    }

    private static CatalogQuery fullQuery(String q, String type, String stability, Boolean deprecated,
            String scope, Boolean singleton, String declaresCap, Boolean definesAttrGroups,
            String dataType, String accessType, String storage, String restart,
            Boolean required, Boolean nillable, Boolean expressions,
            String unit, Boolean hasAllowed, Boolean sensitive,
            Boolean hasRequires, Boolean hasAlternatives, String refsCap,
            Boolean readOnly, Boolean runtimeOnly, Boolean global,
            Boolean hasParams, Boolean hasReturnValue) {
        return CatalogQuery.of(q, type, stability, deprecated, scope,
                singleton, declaresCap, definesAttrGroups,
                dataType, accessType, storage, restart, required, nillable, expressions,
                unit, hasAllowed, sensitive, hasRequires, hasAlternatives, refsCap,
                readOnly, runtimeOnly, global, hasParams, hasReturnValue,
                null, null, null, null);
    }

    // ------------------------------------------------------ multi-value parsing

    @Test
    void multiValueSplitsOnComma() {
        CatalogQuery q = CatalogQuery.of(null, "resource,attribute", null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
        assertEquals(List.of("resource", "attribute"), q.types());
    }

    @Test
    void multiValueTrimsAndLowercases() {
        CatalogQuery q = CatalogQuery.of(null, null, " Default , COMMUNITY ", null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
        assertEquals(List.of("default", "community"), q.stabilities());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void multiValueReturnsEmptyForNullBlankEmpty(String value) {
        CatalogQuery q = CatalogQuery.of(null, null, value, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
        assertTrue(q.stabilities().isEmpty());
    }

    @Test
    void multiValueFiltersEmptySegments() {
        CatalogQuery q = CatalogQuery.of(null, null, "default,,community", null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
        assertEquals(List.of("default", "community"), q.stabilities());
    }

    // ------------------------------------------------------ type validation

    @Test
    void typeFilterAcceptsValidTypes() {
        CatalogQuery q = queryWith(null, "resource,attribute,operation,capability", null, null, null, null, null, null);
        assertEquals(List.of("resource", "attribute", "operation", "capability"), q.types());
    }

    @Test
    void typeFilterRejectsInvalidTypes() {
        CatalogQuery q = queryWith(null, "resource,bogus,attribute", null, null, null, null, null, null);
        assertEquals(List.of("resource", "attribute"), q.types());
    }

    @Test
    void typeFilterRejectsAllInvalidTypes() {
        CatalogQuery q = queryWith(null, "foo,bar", null, null, null, null, null, null);
        assertTrue(q.types().isEmpty());
        assertFalse(q.hasTypeFilter());
    }

    // ------------------------------------------------------ sort validation

    @ParameterizedTest(name = "sort \"{0}\" is accepted")
    @ValueSource(strings = {"name", "type", "address", "stability", "NAME", " Address "})
    void sortAcceptsValidFields(String sort) {
        CatalogQuery q = queryWith(null, null, sort, null, null, null, null, null);
        assertTrue(List.of("name", "type", "address", "stability").contains(q.sort()));
    }

    @Test
    void sortRejectsInvalidFieldAndDefaultsToName() {
        CatalogQuery q = queryWith(null, null, "bogus", null, null, null, null, null);
        assertEquals("name", q.sort());
    }

    @Test
    void sortDefaultsToScoreWhenSearchQueryPresent() {
        CatalogQuery q = queryWith("pool", null, null, null, null, null, null, null);
        assertEquals("score", q.sort());
    }

    @Test
    void sortExplicitOverridesScoreDefault() {
        CatalogQuery q = queryWith("pool", null, "name", null, null, null, null, null);
        assertEquals("name", q.sort());
    }

    @Test
    void sortDefaultsToAddressWhenTypeIsResourceOnly() {
        CatalogQuery q = queryWith(null, "resource", null, null, null, null, null, null);
        assertEquals("address", q.sort());
    }

    @Test
    void sortDefaultsToNameWhenTypeIsNotResourceOnly() {
        CatalogQuery q = queryWith(null, "resource,attribute", null, null, null, null, null, null);
        assertEquals("name", q.sort());
    }

    @Test
    void sortDefaultsToNameWhenNoFilters() {
        CatalogQuery q = query();
        assertEquals("name", q.sort());
    }

    // ------------------------------------------------------ order validation

    @Test
    void orderAcceptsDesc() {
        CatalogQuery q = queryWith(null, null, null, "desc", null, null, null, null);
        assertEquals("desc", q.order());
    }

    @Test
    void orderAcceptsDescCaseInsensitive() {
        CatalogQuery q = queryWith(null, null, null, "DESC", null, null, null, null);
        assertEquals("desc", q.order());
    }

    @Test
    void orderDefaultsToAsc() {
        CatalogQuery q = queryWith(null, null, null, null, null, null, null, null);
        assertEquals("asc", q.order());
    }

    @Test
    void orderDefaultsToAscForInvalidValue() {
        CatalogQuery q = queryWith(null, null, null, "random", null, null, null, null);
        assertEquals("asc", q.order());
    }

    // ------------------------------------------------------ limit

    @Test
    void limitDefaultsTo50() {
        CatalogQuery q = queryWith(null, null, null, null, null, null, null, null);
        assertEquals(50, q.limit());
    }

    @Test
    void limitCapsAt500() {
        CatalogQuery q = queryWith(null, null, null, null, 999, null, null, null);
        assertEquals(500, q.limit());
    }

    @Test
    void limitAcceptsValidValue() {
        CatalogQuery q = queryWith(null, null, null, null, 25, null, null, null);
        assertEquals(25, q.limit());
    }

    @Test
    void limitDefaultsForZero() {
        CatalogQuery q = queryWith(null, null, null, null, 0, null, null, null);
        assertEquals(50, q.limit());
    }

    @Test
    void limitDefaultsForNegative() {
        CatalogQuery q = queryWith(null, null, null, null, -10, null, null, null);
        assertEquals(50, q.limit());
    }

    // ------------------------------------------------------ offset

    @Test
    void offsetDefaultsTo0() {
        CatalogQuery q = queryWith(null, null, null, null, null, null, null, null);
        assertEquals(0, q.offset());
    }

    @Test
    void offsetAcceptsValidValue() {
        CatalogQuery q = queryWith(null, null, null, null, null, 100, null, null);
        assertEquals(100, q.offset());
    }

    @Test
    void offsetDefaultsForNegative() {
        CatalogQuery q = queryWith(null, null, null, null, null, -5, null, null);
        assertEquals(0, q.offset());
    }

    @Test
    void offsetAcceptsZero() {
        CatalogQuery q = queryWith(null, null, null, null, null, 0, null, null);
        assertEquals(0, q.offset());
    }

    // ------------------------------------------------------ search query

    @Test
    void hasSearchQueryWhenPresent() {
        CatalogQuery q = queryWith("pool", null, null, null, null, null, null, null);
        assertTrue(q.hasSearchQuery());
        assertEquals("pool", q.q());
    }

    @Test
    void hasSearchQueryStripsWhitespace() {
        CatalogQuery q = queryWith("  pool  ", null, null, null, null, null, null, null);
        assertEquals("pool", q.q());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void noSearchQueryForNullBlankEmpty(String value) {
        CatalogQuery q = queryWith(value, null, null, null, null, null, null, null);
        assertFalse(q.hasSearchQuery());
        assertNull(q.q());
    }

    // ------------------------------------------------------ scope

    @Test
    void scopeIsStripped() {
        CatalogQuery q = CatalogQuery.of(null, null, null, null, "  /subsystem=io  ",
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
        assertEquals("/subsystem=io", q.scope());
        assertTrue(q.hasScopeFilter());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void scopeNullForBlank(String value) {
        CatalogQuery q = CatalogQuery.of(null, null, null, null, value,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
        assertNull(q.scope());
        assertFalse(q.hasScopeFilter());
    }

    // ------------------------------------------------------ declaresCap protocol

    @Test
    void declaresCapYes() {
        CatalogQuery q = queryWith(null, null, null, null, null, null, "yes", null);
        assertTrue(q.hasDeclaresCapFilter());
        assertTrue(q.declaresCapIsYes());
        assertFalse(q.declaresCapIsNo());
        assertNull(q.declaresCapContains());
    }

    @Test
    void declaresCapNo() {
        CatalogQuery q = queryWith(null, null, null, null, null, null, "no", null);
        assertTrue(q.hasDeclaresCapFilter());
        assertFalse(q.declaresCapIsYes());
        assertTrue(q.declaresCapIsNo());
        assertNull(q.declaresCapContains());
    }

    @Test
    void declaresCapContains() {
        CatalogQuery q = queryWith(null, null, null, null, null, null, "contains:io.buffer", null);
        assertTrue(q.hasDeclaresCapFilter());
        assertFalse(q.declaresCapIsYes());
        assertFalse(q.declaresCapIsNo());
        assertEquals("io.buffer", q.declaresCapContains());
    }

    @Test
    void declaresCapContainsEmptyTextReturnsNull() {
        CatalogQuery q = queryWith(null, null, null, null, null, null, "contains:", null);
        assertTrue(q.hasDeclaresCapFilter());
        assertNull(q.declaresCapContains());
    }

    @Test
    void declaresCapIsLowercased() {
        CatalogQuery q = queryWith(null, null, null, null, null, null, "YES", null);
        assertTrue(q.declaresCapIsYes());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void declaresCapNullForBlank(String value) {
        CatalogQuery q = queryWith(null, null, null, null, null, null, value, null);
        assertFalse(q.hasDeclaresCapFilter());
    }

    // ------------------------------------------------------ refsCap protocol

    @Test
    void refsCapYes() {
        CatalogQuery q = queryWith(null, null, null, null, null, null, null, "yes");
        assertTrue(q.hasRefsCapFilter());
        assertTrue(q.refsCapIsYes());
        assertFalse(q.refsCapIsNo());
        assertNull(q.refsCapContains());
    }

    @Test
    void refsCapNo() {
        CatalogQuery q = queryWith(null, null, null, null, null, null, null, "no");
        assertTrue(q.hasRefsCapFilter());
        assertFalse(q.refsCapIsYes());
        assertTrue(q.refsCapIsNo());
    }

    @Test
    void refsCapContains() {
        CatalogQuery q = queryWith(null, null, null, null, null, null, null, "contains:data-source");
        assertTrue(q.hasRefsCapFilter());
        assertEquals("data-source", q.refsCapContains());
    }

    @Test
    void refsCapContainsEmptyTextReturnsNull() {
        CatalogQuery q = queryWith(null, null, null, null, null, null, null, "contains:");
        assertTrue(q.hasRefsCapFilter());
        assertNull(q.refsCapContains());
    }

    @Test
    void refsCapIsLowercased() {
        CatalogQuery q = queryWith(null, null, null, null, null, null, null, "YES");
        assertTrue(q.refsCapIsYes());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void refsCapNullForBlank(String value) {
        CatalogQuery q = queryWith(null, null, null, null, null, null, null, value);
        assertFalse(q.hasRefsCapFilter());
    }

    // ------------------------------------------------------ boolean filter predicates

    @Test
    void noFiltersWhenAllNull() {
        CatalogQuery q = query();
        assertFalse(q.hasSearchQuery());
        assertFalse(q.hasTypeFilter());
        assertFalse(q.hasStabilityFilter());
        assertFalse(q.hasDeprecatedFilter());
        assertFalse(q.hasScopeFilter());
        assertFalse(q.hasSingletonFilter());
        assertFalse(q.hasDeclaresCapFilter());
        assertFalse(q.hasDefinesAttrGroupsFilter());
        assertFalse(q.hasDataTypeFilter());
        assertFalse(q.hasAccessTypeFilter());
        assertFalse(q.hasStorageFilter());
        assertFalse(q.hasRestartFilter());
        assertFalse(q.hasRequiredFilter());
        assertFalse(q.hasNillableFilter());
        assertFalse(q.hasExpressionsFilter());
        assertFalse(q.hasUnitFilter());
        assertFalse(q.hasHasAllowedFilter());
        assertFalse(q.hasSensitiveFilter());
        assertFalse(q.hasHasRequiresFilter());
        assertFalse(q.hasHasAlternativesFilter());
        assertFalse(q.hasRefsCapFilter());
        assertFalse(q.hasReadOnlyFilter());
        assertFalse(q.hasRuntimeOnlyFilter());
        assertFalse(q.hasGlobalFilter());
        assertFalse(q.hasParamsFilter());
        assertFalse(q.hasReturnValueFilter());
    }

    @Test
    void deprecatedFilterDetected() {
        CatalogQuery q = fullQuery(null, null, null, true, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
        assertTrue(q.hasDeprecatedFilter());
        assertTrue(q.deprecated());
    }

    @Test
    void singletonFilterDetected() {
        CatalogQuery q = fullQuery(null, null, null, null, null,
                true, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
        assertTrue(q.hasSingletonFilter());
    }

    @Test
    void requiredFilterDetected() {
        CatalogQuery q = fullQuery(null, null, null, null, null,
                null, null, null, null, null, null, null, true, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
        assertTrue(q.hasRequiredFilter());
    }

    // ------------------------------------------------------ aggregate filter predicates

    @Test
    void hasAnyAttributeFilterWhenDataTypeSet() {
        CatalogQuery q = fullQuery(null, null, null, null, null,
                null, null, null, "string", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
        assertTrue(q.hasAnyAttributeFilter());
        assertFalse(q.hasAnyOperationFilter());
        assertFalse(q.hasAnyResourceFilter());
    }

    @Test
    void hasAnyAttributeFilterWhenSensitiveSet() {
        CatalogQuery q = fullQuery(null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, true, null, null, null, null, null, null, null, null);
        assertTrue(q.hasAnyAttributeFilter());
    }

    @Test
    void hasAnyAttributeFilterWhenRefsCapSet() {
        CatalogQuery q = fullQuery(null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, "yes", null, null, null, null, null);
        assertTrue(q.hasAnyAttributeFilter());
    }

    @Test
    void hasAnyOperationFilterWhenReadOnlySet() {
        CatalogQuery q = fullQuery(null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, true, null, null, null, null);
        assertTrue(q.hasAnyOperationFilter());
        assertFalse(q.hasAnyAttributeFilter());
        assertFalse(q.hasAnyResourceFilter());
    }

    @Test
    void hasAnyOperationFilterWhenHasParamsSet() {
        CatalogQuery q = fullQuery(null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, true, null);
        assertTrue(q.hasAnyOperationFilter());
    }

    @Test
    void hasAnyResourceFilterWhenSingletonSet() {
        CatalogQuery q = fullQuery(null, null, null, null, null,
                true, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
        assertTrue(q.hasAnyResourceFilter());
        assertFalse(q.hasAnyAttributeFilter());
        assertFalse(q.hasAnyOperationFilter());
    }

    @Test
    void hasAnyResourceFilterWhenDeclaresCapSet() {
        CatalogQuery q = fullQuery(null, null, null, null, null,
                null, "yes", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
        assertTrue(q.hasAnyResourceFilter());
    }

    @Test
    void hasAnyResourceFilterWhenDefinesAttrGroupsSet() {
        CatalogQuery q = fullQuery(null, null, null, null, null,
                null, null, true, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
        assertTrue(q.hasAnyResourceFilter());
    }

    @Test
    void noAggregateFiltersWhenAllNull() {
        CatalogQuery q = query();
        assertFalse(q.hasAnyAttributeFilter());
        assertFalse(q.hasAnyOperationFilter());
        assertFalse(q.hasAnyResourceFilter());
    }

    // ------------------------------------------------------ includesType

    @Test
    void includesTypeAllWhenNoTypeFilter() {
        CatalogQuery q = query();
        assertTrue(q.includesType(CatalogType.RESOURCE));
        assertTrue(q.includesType(CatalogType.ATTRIBUTE));
        assertTrue(q.includesType(CatalogType.OPERATION));
        assertTrue(q.includesType(CatalogType.CAPABILITY));
    }

    @Test
    void includesTypeOnlySelected() {
        CatalogQuery q = queryWith(null, "resource,attribute", null, null, null, null, null, null);
        assertTrue(q.includesType(CatalogType.RESOURCE));
        assertTrue(q.includesType(CatalogType.ATTRIBUTE));
        assertFalse(q.includesType(CatalogType.OPERATION));
        assertFalse(q.includesType(CatalogType.CAPABILITY));
    }

    @Test
    void includesTypeSingle() {
        CatalogQuery q = queryWith(null, "capability", null, null, null, null, null, null);
        assertFalse(q.includesType(CatalogType.RESOURCE));
        assertTrue(q.includesType(CatalogType.CAPABILITY));
    }

    // ------------------------------------------------------ multi-value filter fields

    @ParameterizedTest(name = "dataType \"{0}\" parsed correctly")
    @CsvSource({
            "'string,boolean', 2",
            "'string', 1",
            "'', 0"
    })
    void dataTypeParsing(String input, int expectedSize) {
        CatalogQuery q = fullQuery(null, null, null, null, null,
                null, null, null, input.isEmpty() ? null : input, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
        assertEquals(expectedSize, q.dataTypes().size());
        assertEquals(expectedSize > 0, q.hasDataTypeFilter());
    }

    @Test
    void accessTypeParsing() {
        CatalogQuery q = fullQuery(null, null, null, null, null,
                null, null, null, null, "read-write,read-only", null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
        assertEquals(List.of("read-write", "read-only"), q.accessTypes());
        assertTrue(q.hasAccessTypeFilter());
    }

    @Test
    void unitParsing() {
        CatalogQuery q = fullQuery(null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                "milliseconds,bytes", null, null, null, null, null,
                null, null, null, null, null);
        assertEquals(List.of("milliseconds", "bytes"), q.units());
        assertTrue(q.hasUnitFilter());
    }

    // ------------------------------------------------------ security: injection prevention

    @ParameterizedTest(name = "sort injection attempt: \"{0}\"")
    @ValueSource(strings = {
            "name; MATCH (n) DETACH DELETE n//",
            "name UNION ALL MATCH (n) RETURN n//",
            "1=1 OR 1=1",
            "name\nMATCH (n) DETACH DELETE n",
            "name\"; DROP DATABASE neo4j;//",
            "address DESC, name ASC\nCALL apoc.export.csv.all()",
            "') OR 1=1 RETURN n//"
    })
    void sortRejectsInjectionAttempts(String maliciousSort) {
        CatalogQuery q = queryWith(null, null, maliciousSort, null, null, null, null, null);
        assertEquals("name", q.sort());
    }

    @ParameterizedTest(name = "order injection attempt: \"{0}\"")
    @ValueSource(strings = {
            "asc; MATCH (n) DETACH DELETE n",
            "DESC UNION MATCH (n) RETURN n",
            "asc\nMATCH (n) DELETE n"
    })
    void orderRejectsInjectionAttempts(String maliciousOrder) {
        CatalogQuery q = queryWith(null, null, null, maliciousOrder, null, null, null, null);
        assertEquals("asc", q.order());
    }

    @ParameterizedTest(name = "type injection attempt: \"{0}\"")
    @ValueSource(strings = {
            "resource; DETACH DELETE n",
            "resource' OR '1'='1",
            "resource\nMATCH (n) DELETE n"
    })
    void typeRejectsInjectionAttempts(String maliciousType) {
        CatalogQuery q = queryWith(null, maliciousType, null, null, null, null, null, null);
        assertTrue(q.types().isEmpty());
    }
}
