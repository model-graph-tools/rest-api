package org.wildfly.modelgraph.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
class CapabilityResourceIT {

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
                .body("references[0].resourceAddress", notNullValue());
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
}
