package org.wildfly.modelgraph.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
class AttributeDetailResourceIT {

    @Test
    void attributeDetailReturnsFullResponse() {
        given()
                .when().get("/api/attribute?resource=/subsystem=datasources/data-source=*&name=connection-url")
                .then()
                .statusCode(200)
                .body("name", is("connection-url"))
                .body("type", notNullValue())
                .body("stability", notNullValue())
                .body("accessType", notNullValue())
                .body("storage", notNullValue())
                .body("resourceName", notNullValue())
                .body("resourceAddress", is("/subsystem=datasources/data-source=*"));
    }

    @Test
    void attributeDetailWithCapabilityReference() {
        given()
                .when().get("/api/attribute?resource=/subsystem=datasources/data-source=*&name=datasource-class")
                .then()
                .statusCode(200)
                .body("name", is("datasource-class"));
    }

    @Test
    void attributeDetailSensitiveAttribute() {
        given()
                .when().get("/api/attribute?resource=/subsystem=datasources/data-source=*&name=password")
                .then()
                .statusCode(200)
                .body("name", is("password"))
                .body("sensitive", is(true));
    }

    @Test
    void unknownAttributeReturns404() {
        given()
                .when().get("/api/attribute?resource=/subsystem=datasources/data-source=*&name=nonexistent")
                .then()
                .statusCode(404)
                .body("error", notNullValue());
    }

    @Test
    void unknownResourceReturns404() {
        given()
                .when().get("/api/attribute?resource=/subsystem=nonexistent&name=foo")
                .then()
                .statusCode(404)
                .body("error", notNullValue());
    }

    @Test
    void missingParametersReturns400() {
        given()
                .when().get("/api/attribute")
                .then()
                .statusCode(400);

        given()
                .when().get("/api/attribute?resource=/subsystem=io")
                .then()
                .statusCode(400);

        given()
                .when().get("/api/attribute?name=foo")
                .then()
                .statusCode(400);
    }
}
