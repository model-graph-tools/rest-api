package org.wildfly.modelgraph.api;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class IdentityResourceTest {

    @InjectMock
    ModelGraphRepository repository;

    @Test
    void identityReturnsGraphIdentity() {
        Mockito.when(repository.identity())
                .thenReturn(new IdentityResponse(
                        "org.wildfly:wildfly:41.0.1",
                        "org.wildfly",
                        "wildfly",
                        "WildFly",
                        "41.0.1",
                        "wf",
                        "A powerful application server",
                        "https://wildfly.org",
                        "https://github.com/wildfly/wildfly",
                        "Apache-2.0"));

        given()
                .when().get("/api/identity")
                .then()
                .statusCode(200)
                .body("identifier", is("org.wildfly:wildfly:41.0.1"))
                .body("groupId", is("org.wildfly"))
                .body("artifactId", is("wildfly"))
                .body("name", is("WildFly"))
                .body("version", is("41.0.1"))
                .body("type", is("wf"))
                .body("description", is("A powerful application server"))
                .body("url", is("https://wildfly.org"))
                .body("scmUrl", is("https://github.com/wildfly/wildfly"))
                .body("licenses", is("Apache-2.0"));
    }

    @Test
    void identityHandlesMissingData() {
        Mockito.when(repository.identity())
                .thenReturn(new IdentityResponse(null, null, null, null, null, null, null, null, null, null));

        given()
                .when().get("/api/identity")
                .then()
                .statusCode(200)
                .body("identifier", nullValue())
                .body("version", nullValue());
    }
}
