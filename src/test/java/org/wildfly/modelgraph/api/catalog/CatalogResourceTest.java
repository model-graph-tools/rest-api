package org.wildfly.modelgraph.api.catalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@QuarkusTest
class CatalogResourceTest {

    @InjectMock
    CatalogRepository repository;

    @Test
    void unfilteredRequestReturnsResults() {
        List<CatalogResult> results = List.of(
                new CatalogResult("resource", "buffer-pool", "A buffer pool", "/subsystem=io/buffer-pool=*",
                        null, "default", false, null),
                new CatalogResult("attribute", "enabled", "Whether enabled", "/subsystem=io/buffer-pool=*/enabled",
                        "/subsystem=io/buffer-pool=*", "default", false,
                        new AttributeProperties("boolean", "read-write", "configuration", "all-services",
                                false, true, true, false, null, null)));
        CatalogFacets facets = CatalogFacets.commonOnly(
                Map.of("resource", 10L, "attribute", 20L, "operation", 5L, "capability", 3L),
                Map.of("default", 30L, "community", 5L, "preview", 3L),
                Map.of("true", 2L, "false", 36L));

        Mockito.when(repository.search(any(CatalogQuery.class))).thenReturn(results);
        Mockito.when(repository.total(any(CatalogQuery.class))).thenReturn(38);
        Mockito.when(repository.facetCounts(any(CatalogQuery.class))).thenReturn(facets);

        given()
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("results", hasSize(2))
                .body("results[0].type", is("resource"))
                .body("results[0].name", is("buffer-pool"))
                .body("results[1].type", is("attribute"))
                .body("results[1].properties.dataType", is("boolean"))
                .body("results[1].properties.accessType", is("read-write"))
                .body("facets.type.resource", is(10))
                .body("facets.stability.default", is(30))
                .body("facets.deprecated.true", is(2))
                .body("facets.deprecated.false", is(36))
                .body("total", is(38))
                .body("limit", is(50))
                .body("offset", is(0));
    }

    @Test
    void typeFilterNarrowsResults() {
        List<CatalogResult> results = List.of(
                new CatalogResult("resource", "buffer-pool", "A buffer pool", "/subsystem=io/buffer-pool=*",
                        null, "default", false, null));
        CatalogFacets facets = CatalogFacets.commonOnly(
                Map.of("resource", 10L),
                Map.of("default", 10L),
                Map.of("true", 0L, "false", 10L));

        Mockito.when(repository.search(any(CatalogQuery.class))).thenReturn(results);
        Mockito.when(repository.total(any(CatalogQuery.class))).thenReturn(10);
        Mockito.when(repository.facetCounts(any(CatalogQuery.class))).thenReturn(facets);

        given()
                .queryParam("type", "resource")
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("results", hasSize(1))
                .body("results[0].type", is("resource"));
    }

    @Test
    void defaultPaginationValues() {
        Mockito.when(repository.search(any(CatalogQuery.class))).thenReturn(List.of());
        Mockito.when(repository.total(any(CatalogQuery.class))).thenReturn(0);
        Mockito.when(repository.facetCounts(any(CatalogQuery.class))).thenReturn(CatalogFacets.commonOnly(Map.of(), Map.of(), Map.of()));

        given()
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("limit", is(50))
                .body("offset", is(0));
    }

    @Test
    void singletonFilterPassesToRepository() {
        Mockito.when(repository.search(any(CatalogQuery.class))).thenReturn(List.of());
        Mockito.when(repository.total(any(CatalogQuery.class))).thenReturn(0);
        Mockito.when(repository.facetCounts(any(CatalogQuery.class))).thenReturn(CatalogFacets.commonOnly(Map.of(), Map.of(), Map.of()));

        given()
                .queryParam("singleton", true)
                .when().get("/api/catalog")
                .then()
                .statusCode(200);

        Mockito.verify(repository).search(Mockito.argThat(filter ->
                filter.hasSingletonFilter() && filter.singleton()));
    }

    @Test
    void declaresCapFilterPassesToRepository() {
        Mockito.when(repository.search(any(CatalogQuery.class))).thenReturn(List.of());
        Mockito.when(repository.total(any(CatalogQuery.class))).thenReturn(0);
        Mockito.when(repository.facetCounts(any(CatalogQuery.class))).thenReturn(CatalogFacets.commonOnly(Map.of(), Map.of(), Map.of()));

        given()
                .queryParam("declaresCap", "contains:io")
                .when().get("/api/catalog")
                .then()
                .statusCode(200);

        Mockito.verify(repository).search(Mockito.argThat(filter ->
                filter.hasDeclaresCapFilter() && "io".equals(filter.declaresCapContains())));
    }

    @Test
    void resourceResultsIncludeProperties() {
        List<CatalogResult> results = List.of(
                new CatalogResult("resource", "buffer-pool", "A buffer pool", "/subsystem=io/buffer-pool=*",
                        null, "default", false, new ResourceProperties(true, List.of(), true)));
        CatalogFacets facets = CatalogFacets.commonOnly(
                Map.of("resource", 1L),
                Map.of("default", 1L),
                Map.of());

        Mockito.when(repository.search(any(CatalogQuery.class))).thenReturn(results);
        Mockito.when(repository.total(any(CatalogQuery.class))).thenReturn(1);
        Mockito.when(repository.facetCounts(any(CatalogQuery.class))).thenReturn(facets);

        given()
                .queryParam("type", "resource")
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("results[0].properties.singleton", is(true))
                .body("results[0].properties.capabilities", is(empty()))
                .body("results[0].properties.definesAttrGroups", is(true));
    }

    @Test
    void customPaginationValues() {
        Mockito.when(repository.search(any(CatalogQuery.class))).thenReturn(List.of());
        Mockito.when(repository.total(any(CatalogQuery.class))).thenReturn(0);
        Mockito.when(repository.facetCounts(any(CatalogQuery.class))).thenReturn(CatalogFacets.commonOnly(Map.of(), Map.of(), Map.of()));

        given()
                .queryParam("limit", 25)
                .queryParam("offset", 100)
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("limit", is(25))
                .body("offset", is(100));
    }

    @Test
    void searchQueryPassesToRepository() {
        Mockito.when(repository.search(any(CatalogQuery.class))).thenReturn(List.of());
        Mockito.when(repository.total(any(CatalogQuery.class))).thenReturn(0);
        Mockito.when(repository.facetCounts(any(CatalogQuery.class))).thenReturn(CatalogFacets.commonOnly(Map.of(), Map.of(), Map.of()));

        given()
                .queryParam("q", "pool")
                .when().get("/api/catalog")
                .then()
                .statusCode(200);

        ArgumentCaptor<CatalogQuery> captor = ArgumentCaptor.forClass(CatalogQuery.class);
        Mockito.verify(repository).search(captor.capture());
        CatalogQuery filter = captor.getValue();
        assertTrue(filter.hasSearchQuery());
        assertEquals("pool", filter.q());
    }

    @Test
    void deprecatedFilterPassesToRepository() {
        Mockito.when(repository.search(any(CatalogQuery.class))).thenReturn(List.of());
        Mockito.when(repository.total(any(CatalogQuery.class))).thenReturn(0);
        Mockito.when(repository.facetCounts(any(CatalogQuery.class))).thenReturn(CatalogFacets.commonOnly(Map.of(), Map.of(), Map.of()));

        given()
                .queryParam("deprecated", "true")
                .when().get("/api/catalog")
                .then()
                .statusCode(200);

        ArgumentCaptor<CatalogQuery> captor = ArgumentCaptor.forClass(CatalogQuery.class);
        Mockito.verify(repository).search(captor.capture());
        CatalogQuery filter = captor.getValue();
        assertTrue(filter.hasDeprecatedFilter());
        assertEquals(true, filter.deprecated());
    }

    @Test
    void scopeFilterPassesToRepository() {
        Mockito.when(repository.search(any(CatalogQuery.class))).thenReturn(List.of());
        Mockito.when(repository.total(any(CatalogQuery.class))).thenReturn(0);
        Mockito.when(repository.facetCounts(any(CatalogQuery.class))).thenReturn(CatalogFacets.commonOnly(Map.of(), Map.of(), Map.of()));

        given()
                .queryParam("scope", "/subsystem=io")
                .when().get("/api/catalog")
                .then()
                .statusCode(200);

        ArgumentCaptor<CatalogQuery> captor = ArgumentCaptor.forClass(CatalogQuery.class);
        Mockito.verify(repository).search(captor.capture());
        CatalogQuery filter = captor.getValue();
        assertTrue(filter.hasScopeFilter());
        assertEquals("/subsystem=io", filter.scope());
    }

    @Test
    void sortOrderDefaults() {
        Mockito.when(repository.search(any(CatalogQuery.class))).thenReturn(List.of());
        Mockito.when(repository.total(any(CatalogQuery.class))).thenReturn(0);
        Mockito.when(repository.facetCounts(any(CatalogQuery.class))).thenReturn(CatalogFacets.commonOnly(Map.of(), Map.of(), Map.of()));

        given()
                .when().get("/api/catalog")
                .then()
                .statusCode(200);

        ArgumentCaptor<CatalogQuery> captor = ArgumentCaptor.forClass(CatalogQuery.class);
        Mockito.verify(repository).search(captor.capture());
        CatalogQuery filter = captor.getValue();
        assertEquals("name", filter.sort());
        assertEquals("asc", filter.order());
    }

    @Test
    void sortOrderWithSearchQueryDefaultsToScore() {
        Mockito.when(repository.search(any(CatalogQuery.class))).thenReturn(List.of());
        Mockito.when(repository.total(any(CatalogQuery.class))).thenReturn(0);
        Mockito.when(repository.facetCounts(any(CatalogQuery.class))).thenReturn(CatalogFacets.commonOnly(Map.of(), Map.of(), Map.of()));

        given()
                .queryParam("q", "pool")
                .when().get("/api/catalog")
                .then()
                .statusCode(200);

        ArgumentCaptor<CatalogQuery> captor = ArgumentCaptor.forClass(CatalogQuery.class);
        Mockito.verify(repository).search(captor.capture());
        CatalogQuery filter = captor.getValue();
        assertEquals("score", filter.sort());
    }

    @Test
    void explicitSortOverridesSearchDefault() {
        Mockito.when(repository.search(any(CatalogQuery.class))).thenReturn(List.of());
        Mockito.when(repository.total(any(CatalogQuery.class))).thenReturn(0);
        Mockito.when(repository.facetCounts(any(CatalogQuery.class))).thenReturn(CatalogFacets.commonOnly(Map.of(), Map.of(), Map.of()));

        given()
                .queryParam("q", "pool")
                .queryParam("sort", "name")
                .when().get("/api/catalog")
                .then()
                .statusCode(200);

        ArgumentCaptor<CatalogQuery> captor = ArgumentCaptor.forClass(CatalogQuery.class);
        Mockito.verify(repository).search(captor.capture());
        CatalogQuery filter = captor.getValue();
        assertEquals("name", filter.sort());
    }

    @Test
    void dataTypeMultiValueFilterPassesThrough() {
        List<CatalogResult> results = List.of(
                new CatalogResult("attribute", "max-threads", "Maximum threads", "/subsystem=io/worker=*/max-threads",
                        "/subsystem=io/worker=*", "default", false,
                        new AttributeProperties("int", "read-write", "configuration", "all-services",
                                false, false, true, false, null, null)),
                new CatalogResult("attribute", "enabled", "Whether enabled", "/subsystem=io/buffer-pool=*/enabled",
                        "/subsystem=io/buffer-pool=*", "default", false,
                        new AttributeProperties("boolean", "read-write", "configuration", "all-services",
                                false, true, true, false, null, null)));
        CatalogFacets facets = new CatalogFacets(
                Map.of("attribute", 2L), Map.of(), Map.of(),
                null, null, null,
                Map.of("int", 1L, "boolean", 1L), null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);

        Mockito.when(repository.search(any(CatalogQuery.class))).thenReturn(results);
        Mockito.when(repository.total(any(CatalogQuery.class))).thenReturn(2);
        Mockito.when(repository.facetCounts(any(CatalogQuery.class))).thenReturn(facets);

        given()
                .queryParam("dataType", "int,boolean")
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("results", hasSize(2))
                .body("results[0].properties.dataType", is("int"))
                .body("results[1].properties.dataType", is("boolean"));
    }

    @Test
    void requiredBooleanFilterPassesThrough() {
        List<CatalogResult> results = List.of(
                new CatalogResult("attribute", "name", "The name", "/subsystem=datasources/data-source=*/name",
                        "/subsystem=datasources/data-source=*", "default", false,
                        new AttributeProperties("string", "read-write", "configuration", "all-services",
                                true, false, true, false, null, null)));
        CatalogFacets facets = new CatalogFacets(
                Map.of("attribute", 1L), Map.of(), Map.of(),
                null, null, null,
                null, null, null, null, Map.of("yes", 1L, "no", 0L), null, null, null, null, null, null, null, null,
                null, null, null, null, null);

        Mockito.when(repository.search(any(CatalogQuery.class))).thenReturn(results);
        Mockito.when(repository.total(any(CatalogQuery.class))).thenReturn(1);
        Mockito.when(repository.facetCounts(any(CatalogQuery.class))).thenReturn(facets);

        given()
                .queryParam("required", true)
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("results", hasSize(1))
                .body("results[0].properties.required", is(true));
    }

    // ------------------------------------------------------ operation filter tests

    @Test
    void readOnlyBooleanFilterPassesThrough() {
        List<CatalogResult> results = List.of(
                new CatalogResult("operation", "read-resource", "Reads resource",
                        "/subsystem=io/buffer-pool=*/read-resource",
                        "/subsystem=io/buffer-pool=*", "default", false,
                        new OperationProperties(true, false, true, false, true)));
        CatalogFacets facets = new CatalogFacets(
                Map.of("operation", 5L), Map.of(), Map.of(),
                null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                Map.of("yes", 3L, "no", 2L), null, null, null, null);

        Mockito.when(repository.search(any(CatalogQuery.class))).thenReturn(results);
        Mockito.when(repository.total(any(CatalogQuery.class))).thenReturn(5);
        Mockito.when(repository.facetCounts(any(CatalogQuery.class))).thenReturn(facets);

        given()
                .queryParam("type", "operation")
                .queryParam("readOnly", true)
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("results", hasSize(1))
                .body("results[0].type", is("operation"))
                .body("results[0].properties.readOnly", is(true))
                .body("results[0].properties.global", is(true))
                .body("results[0].properties.hasReturnValue", is(true))
                .body("facets.readOnly.yes", is(3));
    }

    @Test
    void sensitiveFilterPassesToRepository() {
        Mockito.when(repository.search(any(CatalogQuery.class))).thenReturn(List.of());
        Mockito.when(repository.total(any(CatalogQuery.class))).thenReturn(0);
        Mockito.when(repository.facetCounts(any(CatalogQuery.class))).thenReturn(CatalogFacets.commonOnly(Map.of(), Map.of(), Map.of()));

        given()
                .queryParam("sensitive", true)
                .when().get("/api/catalog")
                .then()
                .statusCode(200);

        Mockito.verify(repository).search(Mockito.argThat(filter ->
                filter.hasSensitiveFilter() && filter.sensitive()));
    }

    @Test
    void refsCapFilterPassesToRepository() {
        Mockito.when(repository.search(any(CatalogQuery.class))).thenReturn(List.of());
        Mockito.when(repository.total(any(CatalogQuery.class))).thenReturn(0);
        Mockito.when(repository.facetCounts(any(CatalogQuery.class))).thenReturn(CatalogFacets.commonOnly(Map.of(), Map.of(), Map.of()));

        given()
                .queryParam("refsCap", "contains:io")
                .when().get("/api/catalog")
                .then()
                .statusCode(200);

        Mockito.verify(repository).search(Mockito.argThat(filter ->
                filter.hasRefsCapFilter() && "io".equals(filter.refsCapContains())));
    }

    @Test
    void zeroCountFacetValuesPreservedInResponse() {
        CatalogFacets facets = new CatalogFacets(
                new LinkedHashMap<>(Map.of("resource", 10L, "attribute", 20L, "operation", 0L, "capability", 0L)),
                new LinkedHashMap<>(Map.of("default", 30L, "community", 0L, "preview", 0L, "experimental", 0L)),
                new LinkedHashMap<>(Map.of("true", 0L, "false", 30L)),
                null, null, null,
                new LinkedHashMap<>(Map.of("string", 10L, "int", 5L, "long", 0L, "boolean", 3L,
                        "double", 0L, "big-decimal", 0L, "list", 0L, "object", 2L)),
                new LinkedHashMap<>(Map.of("read-write", 15L, "read-only", 5L, "metric", 0L)),
                null, null,
                new LinkedHashMap<>(Map.of("yes", 5L, "no", 25L)),
                null, null, null, null, null, null, null, null,
                null, null, null, null, null);

        Mockito.when(repository.search(any(CatalogQuery.class))).thenReturn(List.of());
        Mockito.when(repository.total(any(CatalogQuery.class))).thenReturn(30);
        Mockito.when(repository.facetCounts(any(CatalogQuery.class))).thenReturn(facets);

        given()
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("facets.type.operation", is(0))
                .body("facets.type.capability", is(0))
                .body("facets.stability.community", is(0))
                .body("facets.stability.preview", is(0))
                .body("facets.stability.experimental", is(0))
                .body("facets.deprecated.true", is(0))
                .body("facets.dataType.long", is(0))
                .body("facets.dataType.double", is(0))
                .body("facets.accessType.metric", is(0))
                .body("facets.required.yes", is(5));
    }

    @Test
    void hasParamsBooleanFilterPassesThrough() {
        List<CatalogResult> results = List.of(
                new CatalogResult("operation", "add", "Adds resource",
                        "/subsystem=io/buffer-pool=*/add",
                        "/subsystem=io/buffer-pool=*", "default", false,
                        new OperationProperties(false, false, false, true, false)));
        CatalogFacets facets = new CatalogFacets(
                Map.of("operation", 3L), Map.of(), Map.of(),
                null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, Map.of("yes", 2L, "no", 1L), null);

        Mockito.when(repository.search(any(CatalogQuery.class))).thenReturn(results);
        Mockito.when(repository.total(any(CatalogQuery.class))).thenReturn(3);
        Mockito.when(repository.facetCounts(any(CatalogQuery.class))).thenReturn(facets);

        given()
                .queryParam("type", "operation")
                .queryParam("hasParams", true)
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("results", hasSize(1))
                .body("results[0].type", is("operation"))
                .body("results[0].properties.hasParams", is(true))
                .body("results[0].properties.readOnly", is(false))
                .body("facets.hasParams.yes", is(2));
    }
}
