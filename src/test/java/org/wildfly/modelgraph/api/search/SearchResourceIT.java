package org.wildfly.modelgraph.api.search;

import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;
import org.wildfly.modelgraph.api.IntegrationTestProfile;

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
                .body("results.find { it.type == 'resource' }.name", is("buffer-pool"))
                .body("results.find { it.type == 'resource' }.address", containsString("buffer-pool"))
                .body("results.find { it.type == 'resource' }.stability", notNullValue())
                .body("results.find { it.type == 'resource' }.singleton", notNullValue());
    }

    @Test
    void searchForAttribute() {
        given()
                .queryParam("q", "enabled")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results", not(empty()))
                .body("results.findAll { it.type == 'attribute' }", not(empty()));
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

        var types = List.of("resource", "attribute", "capability", "operation");
        for (String type : types) {
            List<String> matched = results.getList(
                    "results.findAll { it.type == '" + type + "' }.name");
            assertThat("Too many " + type + " results for limit=2",
                    matched.size(), lessThanOrEqualTo(2));
        }
    }

    @Test
    void searchOrdersNameMatchesBeforeDescriptionMatches() {
        var results = given()
                .queryParam("q", "buffer-pool")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results", not(empty()))
                .extract().jsonPath();

        List<Map<String, String>> resources = results.getList(
                "results.findAll { it.type == 'resource' }");
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
    void searchForHyphenatedResource() {
        given()
                .queryParam("q", "http-listener")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results", not(empty()))
                .body("results.find { it.type == 'resource' }.name", containsString("http-listener"));
    }

    @Test
    void searchForHyphenatedAttribute() {
        given()
                .queryParam("q", "max-pool-size")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results", not(empty()))
                .body("results.findAll { it.type == 'attribute' }.name", hasItem(containsString("max-pool-size")));
    }

    @Test
    void searchForHyphenatedOperation() {
        given()
                .queryParam("q", "add-alias")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results", not(empty()))
                .body("results.findAll { it.type == 'operation' }.name", hasItem(containsString("add-alias")));
    }

    @Test
    void searchForDottedCapability() {
        given()
                .queryParam("q", "org.wildfly.io.buffer-pool")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results", not(empty()))
                .body("results.find { it.type == 'capability' }.name", is("org.wildfly.io.buffer-pool"));
    }

    @Test
    void searchForCapabilityBySegment() {
        given()
                .queryParam("q", "batch")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results.findAll { it.type == 'capability' }", not(empty()));
    }

    @Test
    void searchForCapabilityByHyphenatedSegment() {
        given()
                .queryParam("q", "data-source")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results.findAll { it.type == 'capability' }.name",
                        hasItem(containsString("data-source")));
    }

    @Test
    void searchForCapabilityByDehyphenatedTerm() {
        given()
                .queryParam("q", "datasource")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results.findAll { it.type == 'capability' }.name",
                        hasItem(containsString("data-source")));
    }

    @Test
    void searchForCapabilityByElytronSegment() {
        given()
                .queryParam("q", "elytron")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results.findAll { it.type == 'capability' }", not(empty()))
                .body("results.findAll { it.type == 'capability' }.name",
                        everyItem(containsString("elytron")));
    }

    @Test
    void searchForCapabilityBySegmentIncludesProvidedBy() {
        var results = given()
                .queryParam("q", "batch")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .extract().jsonPath();

        List<Map<String, Object>> capabilities = results.getList(
                "results.findAll { it.type == 'capability' }");
        assertThat("Expected capability results for 'batch'", capabilities, not(empty()));
        boolean anyHasProvidedBy = capabilities.stream()
                .anyMatch(c -> c.get("providedBy") != null);
        assertThat("At least one capability should have providedBy resources", anyHasProvidedBy, is(true));
    }

    @Test
    void searchForPartialHyphenatedTerm() {
        given()
                .queryParam("q", "buffer-p")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results", not(empty()))
                .body("results.name", hasItem(containsString("buffer-pool")));
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
        var response = given()
                .queryParam("q", "datasource")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results", not(empty()))
                .body("results.type", everyItem(oneOf("resource", "attribute", "capability", "operation")))
                .body("results.name", everyItem(notNullValue()))
                .extract().jsonPath();

        List<Map<String, Object>> results = response.getList("results");
        for (Map<String, Object> result : results) {
            if ("capability".equals(result.get("type"))) {
                assertThat("Capability stability should be null (stability is per-declaration)",
                        result.get("stability"), nullValue());
            } else {
                assertThat("Non-capability result should have stability",
                        result.get("stability"), notNullValue());
            }
        }
    }
}
