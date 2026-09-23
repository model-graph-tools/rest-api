package org.wildfly.modelgraph.api;

import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

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
    void searchWithLimitRespectsLimitPerType() {
        var results = given()
                .queryParam("q", "pool")
                .queryParam("limit", 2)
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .extract().jsonPath();

        var types = List.of("Resource", "Attribute", "Capability", "Operation");
        for (String type : types) {
            List<String> matched = results.getList(
                    "results.findAll { it.type == '" + type + "' }.name");
            assertThat("Too many " + type + " results for limit=2",
                    matched.size(), lessThanOrEqualTo(2));
        }
    }

    @Test
    void searchOrdersNameMatchesBeforeDescriptionMatches() {
        // "buffer-pool" matches resources by name; search for it and verify
        // that results with the term in the name appear before results that
        // only match in the description.
        var results = given()
                .queryParam("q", "buffer-pool")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results", not(empty()))
                .extract().jsonPath();

        List<Map<String, String>> resources = results.getList(
                "results.findAll { it.type == 'Resource' }");
        if (resources.size() > 1) {
            boolean seenDescriptionOnly = false;
            for (Map<String, String> r : resources) {
                String name = r.get("name");
                boolean nameMatch = name != null && name.contains("buffer-pool");
                if (!nameMatch) {
                    seenDescriptionOnly = true;
                }
                if (seenDescriptionOnly && nameMatch) {
                    fail("Resource with name match '" + name +
                            "' appeared after a description-only match — ORDER BY is broken");
                }
            }
        }
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
                .body("results.type", everyItem(isOneOf("Resource", "Attribute", "Capability", "Operation")))
                .body("results.name", everyItem(notNullValue()));
    }
}
