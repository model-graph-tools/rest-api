package org.wildfly.modelgraph.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
class VersionResourceIT {

    @Test
    void versionReturnsWildFlyVersion() {
        given()
                .when().get("/api/version")
                .then()
                .statusCode(200)
                .body("version", notNullValue())
                .body("version", matchesRegex("\\d+\\.\\d+(\\.\\d+)?.*"))
                .body("sourceType", is("wildfly"));
    }
}
