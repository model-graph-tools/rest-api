package org.wildfly.modelgraph.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
class SearchResourceIT {

    @Test
    void searchForResourceByName() {
        given()
                .queryParam("q", "buffer-pool")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results", not(empty()))
                .body("results.find { it.type == 'Resource' }.name", is("buffer-pool"))
                .body("results.find { it.type == 'Resource' }.address", containsString("buffer-pool"));
    }

    @Test
    void searchForAttribute() {
        given()
                .queryParam("q", "enabled")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results", not(empty()))
                .body("results.findAll { it.type == 'Attribute' }", not(empty()));
    }

    @Test
    void searchExcludesDeploymentResources() {
        given()
                .queryParam("q", "subsystem")
                .queryParam("limit", 100)
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results.findAll { it.address != null }.address",
                        everyItem(not(startsWith("/deployment"))));
    }

    @Test
    void searchWithLimitRespectsLimit() {
        given()
                .queryParam("q", "pool")
                .queryParam("limit", 3)
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results.size()", lessThanOrEqualTo(9));
    }

    @Test
    void searchForNonexistentTermReturnsEmpty() {
        given()
                .queryParam("q", "zzz-nonexistent-zzz")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results", empty());
    }

    @Test
    void searchResultsHaveRequiredFields() {
        given()
                .queryParam("q", "datasource")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results", not(empty()))
                .body("results.type", everyItem(isOneOf("Resource", "Attribute", "Capability")))
                .body("results.name", everyItem(notNullValue()));
    }
}
