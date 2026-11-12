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
class ResourceDetailResourceIT {

    @Test
    void resourceDetailReturnsFullResponse() {
        given()
                .when().get("/api/resource?address=/subsystem=io")
                .then()
                .statusCode(200)
                .body("name", notNullValue())
                .body("address", is("/subsystem=io"))
                .body("stability", notNullValue())
                .body("childResources", not(empty()))
                .body("attributes", not(empty()))
                .body("operations", not(empty()));
    }

    @Test
    void resourceDetailChildResourcesSortedByName() {
        List<String> names = given()
                .when().get("/api/resource?address=/subsystem=io")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("childResources.name", String.class);
        List<String> sorted = names.stream().sorted().toList();
        assertEquals(sorted, names);
    }

    @Test
    void resourceDetailAttributesSortedByName() {
        List<String> names = given()
                .when().get("/api/resource?address=/subsystem=io")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("attributes.name", String.class);
        List<String> sorted = names.stream().sorted().toList();
        assertEquals(sorted, names);
    }

    @Test
    void resourceDetailOperationsSortedByName() {
        List<String> names = given()
                .when().get("/api/resource?address=/subsystem=io")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("operations.name", String.class);
        List<String> sorted = names.stream().sorted().toList();
        assertEquals(sorted, names);
    }

    @Test
    void resourceDetailIncludesParentAddress() {
        given()
                .when().get("/api/resource?address=/subsystem=io/buffer-pool=*")
                .then()
                .statusCode(200)
                .body("parentAddress", is("/subsystem=io"));
    }

    @Test
    void resourceDetailSingletonResource() {
        given()
                .when().get("/api/resource?address=/subsystem=mail/mail-session=*/server=smtp")
                .then()
                .statusCode(200)
                .body("singleton", is(true));
    }

    @Test
    void unknownResourceReturns404() {
        given()
                .when().get("/api/resource?address=/subsystem=nonexistent")
                .then()
                .statusCode(404)
                .body("error", notNullValue())
                .body("address", is("/subsystem=nonexistent"));
    }

    @Test
    void missingAddressReturns400() {
        given()
                .when().get("/api/resource")
                .then()
                .statusCode(400)
                .body("error", notNullValue());
    }
}
