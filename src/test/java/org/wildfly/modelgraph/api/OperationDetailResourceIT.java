package org.wildfly.modelgraph.api;

import java.util.List;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
class OperationDetailResourceIT {

    @Test
    void operationDetailReturnsFullResponse() {
        given()
                .when().get("/api/operation?resource=/subsystem=datasources/data-source=*&name=add")
                .then()
                .statusCode(200)
                .body("name", is("add"))
                .body("stability", notNullValue())
                .body("resourceAddress", is("/subsystem=datasources/data-source=*"))
                .body("parameters", not(empty()));
    }

    @Test
    void operationDetailParametersSortedByName() {
        List<String> names = given()
                .when().get("/api/operation?resource=/subsystem=datasources/data-source=*&name=add")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("parameters.name", String.class);
        List<String> sorted = names.stream().sorted().toList();
        assertEquals(sorted, names);
    }

    @Test
    void operationDetailReadOnlyOperation() {
        given()
                .when().get("/api/operation?resource=/subsystem=datasources/data-source=*&name=test-connection-in-pool")
                .then()
                .statusCode(200)
                .body("name", is("test-connection-in-pool"));
    }

    @Test
    void unknownOperationReturns404() {
        given()
                .when().get("/api/operation?resource=/subsystem=datasources/data-source=*&name=nonexistent")
                .then()
                .statusCode(404)
                .body("error", notNullValue());
    }

    @Test
    void unknownResourceReturns404() {
        given()
                .when().get("/api/operation?resource=/subsystem=nonexistent&name=add")
                .then()
                .statusCode(404)
                .body("error", notNullValue());
    }

    @Test
    void missingParametersReturns400() {
        given()
                .when().get("/api/operation")
                .then()
                .statusCode(400);

        given()
                .when().get("/api/operation?resource=/subsystem=io")
                .then()
                .statusCode(400);

        given()
                .when().get("/api/operation?name=add")
                .then()
                .statusCode(400);
    }
}
