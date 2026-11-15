package org.wildfly.modelgraph.api.version;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;
import org.wildfly.modelgraph.api.IntegrationTestProfile;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
class VersionResourceIT {

    @Test
    void versionReturnsApiVersion() {
        given()
                .when().get("/api/version")
                .then()
                .statusCode(200)
                .body("version", notNullValue())
                .body("version", matchesRegex("\\d+\\.\\d+(\\.\\d+)?.*"));
    }
}
