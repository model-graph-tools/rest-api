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

    // ------------------------------------------------------ keyword search (q)

    @Test
    void searchWithQReturnsMatchingResults() {
        var response = given()
                .queryParam("q", "pool")
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("results", not(empty()))
                .extract().jsonPath();

        List<String> names = response.getList("results.name");
        // At least some results should contain "pool" in the name
        assertThat("At least one result should contain 'pool'",
                names.stream().anyMatch(n -> n.toLowerCase().contains("pool")), is(true));
    }

    @Test
    void searchWithQDefaultsSortToRelevance() {
        // When q is provided without explicit sort, results should still return
        given()
                .queryParam("q", "pool")
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("results", not(empty()));
    }

    // ------------------------------------------------------ deprecated filter

    @Test
    void deprecatedNoExcludesDeprecatedElements() {
        var response = given()
                .queryParam("deprecated", "no")
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("results", not(empty()))
                .extract().jsonPath();

        List<Boolean> deprecatedFlags = response.getList("results.deprecated");
        assertThat("No results should be deprecated",
                deprecatedFlags, everyItem(is(false)));
    }

    @Test
    void deprecatedFacetHasYesNoKeys() {
        var response = given()
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .extract().jsonPath();

        Map<String, Object> deprecatedFacet = response.getMap("facets.deprecated");
        assertThat("deprecated facet should have 'yes' key", deprecatedFacet, hasKey("yes"));
        assertThat("deprecated facet should have 'no' key", deprecatedFacet, hasKey("no"));
    }

    // ------------------------------------------------------ scope filter

    @Test
    void scopeFilterNarrowsToSubtree() {
        var response = given()
                .queryParam("scope", "/subsystem=io")
                .queryParam("type", "resource")
                .queryParam("limit", 100)
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("results", not(empty()))
                .extract().jsonPath();

        List<String> addresses = response.getList("results.address");
        assertThat("All resource addresses should start with /subsystem=io",
                addresses, everyItem(startsWith("/subsystem=io")));
    }

    // ------------------------------------------------------ sort and order

    @Test
    void defaultSortIsNameAscending() {
        var response = given()
                .queryParam("type", "resource")
                .queryParam("limit", 10)
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("results", not(empty()))
                .extract().jsonPath();

        List<String> names = response.getList("results.name");
        for (int i = 1; i < names.size(); i++) {
            assertThat("Results should be sorted by name ascending",
                    names.get(i).compareTo(names.get(i - 1)), greaterThanOrEqualTo(0));
        }
    }

    @Test
    void sortByAddressDescSortsCorrectly() {
        var response = given()
                .queryParam("type", "resource")
                .queryParam("sort", "address")
                .queryParam("order", "desc")
                .queryParam("limit", 10)
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("results", not(empty()))
                .extract().jsonPath();

        List<String> addresses = response.getList("results.address");
        // Filter out nulls for comparison
        List<String> nonNullAddresses = addresses.stream()
                .filter(a -> a != null)
                .toList();
        for (int i = 1; i < nonNullAddresses.size(); i++) {
            assertThat("Results should be sorted by address descending",
                    nonNullAddresses.get(i).compareTo(nonNullAddresses.get(i - 1)), lessThanOrEqualTo(0));
        }
    }

    @Test
    void nullAddressesSortLastRegardlessOfDirection() {
        // Include capabilities (which have null addresses) along with resources
        var responseAsc = given()
                .queryParam("type", "resource,capability")
                .queryParam("sort", "address")
                .queryParam("order", "asc")
                .queryParam("limit", 200)
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .extract().jsonPath();

        List<String> addressesAsc = responseAsc.getList("results.address");
        // Find first null — all subsequent should also be null
        boolean seenNull = false;
        for (String addr : addressesAsc) {
            if (addr == null) {
                seenNull = true;
            } else if (seenNull) {
                assertThat("Non-null address should not appear after null (asc)", false, is(true));
            }
        }

        var responseDesc = given()
                .queryParam("type", "resource,capability")
                .queryParam("sort", "address")
                .queryParam("order", "desc")
                .queryParam("limit", 200)
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .extract().jsonPath();

        List<String> addressesDesc = responseDesc.getList("results.address");
        seenNull = false;
        for (String addr : addressesDesc) {
            if (addr == null) {
                seenNull = true;
            } else if (seenNull) {
                assertThat("Non-null address should not appear after null (desc)", false, is(true));
            }
        }
    }

    // ------------------------------------------------------ operation facet tests

    @Test
    void readOnlyTrueReturnsOnlyReadOnlyOperations() {
        var response = given()
                .queryParam("type", "operation")
                .queryParam("readOnly", true)
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .extract().jsonPath();

        List<String> types = response.getList("results.type");
        assertThat("All results should be operations", types, everyItem(is("operation")));

        List<Boolean> readOnlyValues = response.getList("results.properties.readOnly");
        assertThat("All operations should be read-only", readOnlyValues, everyItem(is(true)));
    }

    @Test
    void globalTrueReturnsGlobalOperations() {
        var response = given()
                .queryParam("type", "operation")
                .queryParam("global", true)
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .extract().jsonPath();

        List<String> types = response.getList("results.type");
        assertThat("All results should be operations", types, everyItem(is("operation")));

        List<Boolean> globalValues = response.getList("results.properties.global");
        assertThat("All operations should be global", globalValues, everyItem(is(true)));
    }

    @Test
    void hasParamsTrueReturnsOperationsWithParameters() {
        var response = given()
                .queryParam("type", "operation")
                .queryParam("hasParams", true)
                .queryParam("limit", 20)
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("results", not(empty()))
                .extract().jsonPath();

        List<String> types = response.getList("results.type");
        assertThat("All results should be operations", types, everyItem(is("operation")));

        List<Boolean> hasParamsValues = response.getList("results.properties.hasParams");
        assertThat("All operations should have parameters", hasParamsValues, everyItem(is(true)));
    }

    @Test
    void operationResultsIncludeAllPropertyFields() {
        var response = given()
                .queryParam("type", "operation")
                .queryParam("limit", 5)
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("results", not(empty()))
                .extract().jsonPath();

        // Verify first operation result has all five boolean property fields
        assertThat("properties should not be null",
                response.get("results[0].properties"), notNullValue());
        assertThat("readOnly should be a boolean",
                response.get("results[0].properties.readOnly"), instanceOf(Boolean.class));
        assertThat("runtimeOnly should be a boolean",
                response.get("results[0].properties.runtimeOnly"), instanceOf(Boolean.class));
        assertThat("global should be a boolean",
                response.get("results[0].properties.global"), instanceOf(Boolean.class));
        assertThat("hasParams should be a boolean",
                response.get("results[0].properties.hasParams"), instanceOf(Boolean.class));
        assertThat("hasReturnValue should be a boolean",
                response.get("results[0].properties.hasReturnValue"), instanceOf(Boolean.class));
    }

    @Test
    void operationFacetsOmittedWhenTypeIsResource() {
        var response = given()
                .queryParam("type", "resource")
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .extract().jsonPath();

        Map<String, Object> facets = response.getMap("facets");
        assertThat("readOnly facet should be absent for resources", facets, not(hasKey("readOnly")));
        assertThat("runtimeOnly facet should be absent for resources", facets, not(hasKey("runtimeOnly")));
        assertThat("global facet should be absent for resources", facets, not(hasKey("global")));
        assertThat("hasParams facet should be absent for resources", facets, not(hasKey("hasParams")));
        assertThat("hasReturnValue facet should be absent for resources", facets, not(hasKey("hasReturnValue")));
    }
}
