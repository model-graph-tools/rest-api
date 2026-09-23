package org.wildfly.modelgraph.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
class IdentityResourceIT {

    @Test
    void identityReturnsGraphIdentity() {
        given()
                .when().get("/api/identity")
                .then()
                .statusCode(200)
                .body("identifier", notNullValue())
                .body("name", notNullValue())
                .body("version", notNullValue())
                .body("version", matchesRegex("\\d+\\.\\d+(\\.\\d+)?.*"))
                .body("type", is("wf"));
    }
}
