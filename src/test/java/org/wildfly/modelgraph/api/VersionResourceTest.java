package org.wildfly.modelgraph.api;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class VersionResourceTest {

    @InjectMock
    ModelGraphRepository repository;

    @Test
    void versionReturnsGraphVersion() {
        Mockito.when(repository.version())
                .thenReturn(new VersionResponse("41.0.1", "wildfly"));

        given()
                .when().get("/api/version")
                .then()
                .statusCode(200)
                .body("version", is("41.0.1"))
                .body("sourceType", is("wildfly"));
    }

    @Test
    void versionHandlesMissingData() {
        Mockito.when(repository.version())
                .thenReturn(new VersionResponse(null, null));

        given()
                .when().get("/api/version")
                .then()
                .statusCode(200)
                .body("version", nullValue())
                .body("sourceType", nullValue());
    }
}
