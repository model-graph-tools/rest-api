package org.wildfly.modelgraph.api;

import java.util.List;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class SearchResourceTest {

    @InjectMock
    ModelGraphRepository repository;

    @Test
    void searchReturnsResults() {
        Mockito.when(repository.search("pool", 10)).thenReturn(List.of(
                new SearchResult("Resource", "buffer-pool", "Defines buffer pool",
                        "/subsystem=io/buffer-pool=*"),
                new SearchResult("Attribute", "buffer-pool", "The listeners buffer pool",
                        "/subsystem=undertow/server=*/http-listener=*")));

        given()
                .queryParam("q", "pool")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results", hasSize(2))
                .body("results[0].type", is("Resource"))
                .body("results[0].name", is("buffer-pool"))
                .body("results[0].address", is("/subsystem=io/buffer-pool=*"))
                .body("results[1].type", is("Attribute"))
                .body("results[1].name", is("buffer-pool"));
    }

    @Test
    void searchWithCustomLimit() {
        Mockito.when(repository.search("pool", 5)).thenReturn(List.of(
                new SearchResult("Resource", "buffer-pool", "Defines buffer pool",
                        "/subsystem=io/buffer-pool=*")));

        given()
                .queryParam("q", "pool")
                .queryParam("limit", 5)
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results", hasSize(1));
    }

    @Test
    void searchWithoutQueryReturnsBadRequest() {
        given()
                .when().get("/api/search")
                .then()
                .statusCode(400)
                .body("error", is("Query parameter 'q' is required"));
    }

    @Test
    void searchWithBlankQueryReturnsBadRequest() {
        given()
                .queryParam("q", "   ")
                .when().get("/api/search")
                .then()
                .statusCode(400);
    }

    @Test
    void searchWithEmptyResultsReturnsEmptyList() {
        Mockito.when(repository.search("nonexistent", 10)).thenReturn(List.of());

        given()
                .queryParam("q", "nonexistent")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results", hasSize(0));
    }
}
