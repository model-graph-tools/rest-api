package org.wildfly.modelgraph.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
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
                        "/subsystem=io/buffer-pool=*", "default", false, null));
        Map<String, Map<String, Long>> facets = new LinkedHashMap<>();
        facets.put("type", Map.of("resource", 10L, "attribute", 20L, "operation", 5L, "capability", 3L));
        facets.put("stability", Map.of("default", 30L, "community", 5L, "preview", 3L));

        Mockito.when(repository.search(any(CatalogFilter.class))).thenReturn(results);
        Mockito.when(repository.total(any(CatalogFilter.class))).thenReturn(38);
        Mockito.when(repository.facetCounts(any(CatalogFilter.class))).thenReturn(facets);

        given()
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("results", hasSize(2))
                .body("results[0].type", is("resource"))
                .body("results[0].name", is("buffer-pool"))
                .body("results[1].type", is("attribute"))
                .body("facets.type.resource", is(10))
                .body("facets.stability.default", is(30))
                .body("total", is(38))
                .body("limit", is(50))
                .body("offset", is(0));
    }

    @Test
    void typeFilterNarrowsResults() {
        List<CatalogResult> results = List.of(
                new CatalogResult("resource", "buffer-pool", "A buffer pool", "/subsystem=io/buffer-pool=*",
                        null, "default", false, null));
        Map<String, Map<String, Long>> facets = new LinkedHashMap<>();
        facets.put("type", Map.of("resource", 10L));
        facets.put("stability", Map.of("default", 10L));

        Mockito.when(repository.search(any(CatalogFilter.class))).thenReturn(results);
        Mockito.when(repository.total(any(CatalogFilter.class))).thenReturn(10);
        Mockito.when(repository.facetCounts(any(CatalogFilter.class))).thenReturn(facets);

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
        Mockito.when(repository.search(any(CatalogFilter.class))).thenReturn(List.of());
        Mockito.when(repository.total(any(CatalogFilter.class))).thenReturn(0);
        Mockito.when(repository.facetCounts(any(CatalogFilter.class))).thenReturn(Map.of());

        given()
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("limit", is(50))
                .body("offset", is(0));
    }

    @Test
    void customPaginationValues() {
        Mockito.when(repository.search(any(CatalogFilter.class))).thenReturn(List.of());
        Mockito.when(repository.total(any(CatalogFilter.class))).thenReturn(0);
        Mockito.when(repository.facetCounts(any(CatalogFilter.class))).thenReturn(Map.of());

        given()
                .queryParam("limit", 25)
                .queryParam("offset", 100)
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("limit", is(25))
                .body("offset", is(100));
    }
}
