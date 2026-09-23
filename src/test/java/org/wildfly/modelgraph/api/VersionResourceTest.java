package org.wildfly.modelgraph.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class VersionResourceTest {

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
