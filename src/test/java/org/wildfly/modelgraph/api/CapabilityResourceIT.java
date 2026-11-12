package org.wildfly.modelgraph.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
class CapabilityResourceIT {

    // ------------------------------------------------------ /references (existing)

    @Test
    void capabilityWithReferencesReturnsResults() {
        given()
                .when().get("/api/capability/org.wildfly.data-source/references")
                .then()
                .statusCode(200)
                .body("capability", is("org.wildfly.data-source"))
                .body("references", not(empty()))
                .body("references[0].attributeName", notNullValue())
                .body("references[0].resourceName", notNullValue())
                .body("references[0].attributeStability", notNullValue())
                .body("references[0].resourceAddress", notNullValue())
                .body("references[0].resourceStability", notNullValue());
    }

    @Test
    void capabilityReferencesExcludeDeployments() {
        given()
                .when().get("/api/capability/org.wildfly.data-source/references")
                .then()
                .statusCode(200)
                .body("references.resourceAddress",
                        everyItem(not(startsWith("/deployment"))));
    }

    @Test
    void unknownCapabilityReturnsEmptyReferences() {
        given()
                .when().get("/api/capability/org.nonexistent.capability/references")
                .then()
                .statusCode(200)
                .body("capability", is("org.nonexistent.capability"))
                .body("references", empty());
    }

    // ------------------------------------------------------ /detail (new)

    @Test
    void capabilityDetailReturnsFullResponse() {
        given()
                .when().get("/api/capability/org.wildfly.data-source")
                .then()
                .statusCode(200)
                .body("name", is("org.wildfly.data-source"))
                .body("stability", notNullValue())
                .body("declaredBy", not(empty()))
                .body("declaredBy[0].resourceName", notNullValue())
                .body("declaredBy[0].resourceAddress", notNullValue())
                .body("referencedBy", not(empty()))
                .body("referencedBy[0].attributeName", notNullValue())
                .body("referencedBy[0].resourceName", notNullValue())
                .body("referencedBy[0].resourceAddress", notNullValue());
    }

    @Test
    void capabilityDetailDeclaredBySortedByName() {
        List<String> names = given()
                .when().get("/api/capability/org.wildfly.data-source")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("declaredBy.resourceName", String.class);
        List<String> sorted = names.stream().sorted().toList();
        assertEquals(sorted, names);
    }

    @Test
    void capabilityDetailReferencedBySortedByAttributeName() {
        List<String> names = given()
                .when().get("/api/capability/org.wildfly.data-source")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("referencedBy.attributeName", String.class);
        List<String> sorted = names.stream().sorted().toList();
        assertEquals(sorted, names);
    }

    @Test
    void unknownCapabilityDetailReturns404() {
        given()
                .when().get("/api/capability/org.nonexistent.capability")
                .then()
                .statusCode(404)
                .body("error", notNullValue())
                .body("name", is("org.nonexistent.capability"));
    }
}
