package org.wildfly.modelgraph.api;

import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
class CatalogResourceIT {

    @Test
    void unfilteredCatalogReturnsAllTypes() {
        given()
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("results", not(empty()))
                .body("total", greaterThan(0))
                .body("limit", is(50))
                .body("offset", is(0));

        // Verify all four types are present via facets
        var response = given()
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .extract().jsonPath();

        Map<String, Object> typeFacets = response.getMap("facets.type");
        assertThat("type facet should contain resource", typeFacets, hasKey("resource"));
        assertThat("type facet should contain attribute", typeFacets, hasKey("attribute"));
        assertThat("type facet should contain operation", typeFacets, hasKey("operation"));
        assertThat("type facet should contain capability", typeFacets, hasKey("capability"));
    }

    @Test
    void typeFacetCountsAreNonZero() {
        var response = given()
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .extract().jsonPath();

        Map<String, Integer> typeFacets = response.getMap("facets.type");
        for (Map.Entry<String, Integer> entry : typeFacets.entrySet()) {
            assertThat("Type facet count for " + entry.getKey() + " should be > 0",
                    entry.getValue(), greaterThan(0));
        }
    }

    @Test
    void stabilityFacetCountsArePresent() {
        given()
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("facets.stability", not(anEmptyMap()));
    }

    @Test
    void typeFilterResourceReturnsOnlyResources() {
        var response = given()
                .queryParam("type", "resource")
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("results", not(empty()))
                .extract().jsonPath();

        List<String> types = response.getList("results.type");
        assertThat("All results should be resources", types, everyItem(is("resource")));
    }

    @Test
    void typeFilterMultipleTypesReturnsMatchingTypes() {
        var response = given()
                .queryParam("type", "resource,attribute")
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("results", not(empty()))
                .extract().jsonPath();

        List<String> types = response.getList("results.type");
        assertThat("Results should only contain resource or attribute",
                types, everyItem(isOneOf("resource", "attribute")));
        // Verify both types are present (if enough results)
        assertThat("Should contain resources", types, hasItem("resource"));
        assertThat("Should contain attributes", types, hasItem("attribute"));
    }

    @Test
    void paginationWithLimitAndOffset() {
        // First page
        var firstPage = given()
                .queryParam("limit", 5)
                .queryParam("offset", 0)
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("results", hasSize(5))
                .body("limit", is(5))
                .body("offset", is(0))
                .extract().jsonPath();

        // Second page
        var secondPage = given()
                .queryParam("limit", 5)
                .queryParam("offset", 5)
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("results", hasSize(5))
                .body("limit", is(5))
                .body("offset", is(5))
                .extract().jsonPath();

        // Pages should have different results
        List<String> firstNames = firstPage.getList("results.name");
        List<String> secondNames = secondPage.getList("results.name");
        assertThat("Pages should have different results", firstNames, not(equalTo(secondNames)));

        // Total should be consistent
        int firstTotal = firstPage.getInt("total");
        int secondTotal = secondPage.getInt("total");
        assertThat("Total should be consistent across pages", firstTotal, is(secondTotal));
    }

    @Test
    void deploymentResourcesAreExcluded() {
        var response = given()
                .queryParam("type", "resource")
                .queryParam("limit", 200)
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .extract().jsonPath();

        List<String> addresses = response.getList("results.address");
        assertThat("No deployment resources should be present",
                addresses, everyItem(not(startsWith("/deployment"))));
    }

    @Test
    void allTypeAndStabilityValuesAreLowercase() {
        var response = given()
                .queryParam("limit", 100)
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .extract().jsonPath();

        List<String> types = response.getList("results.type");
        for (String type : types) {
            assertThat("Type value should be lowercase: " + type, type, is(type.toLowerCase()));
        }

        List<String> stabilities = response.getList("results.findAll { it.stability != null }.stability");
        for (String stability : stabilities) {
            assertThat("Stability value should be lowercase: " + stability, stability, is(stability.toLowerCase()));
        }

        // Check facet keys are lowercase too
        Map<String, Object> typeFacets = response.getMap("facets.type");
        for (String key : typeFacets.keySet()) {
            assertThat("Type facet key should be lowercase: " + key, key, is(key.toLowerCase()));
        }

        Map<String, Object> stabilityFacets = response.getMap("facets.stability");
        for (String key : stabilityFacets.keySet()) {
            assertThat("Stability facet key should be lowercase: " + key, key, is(key.toLowerCase()));
        }
    }
}
