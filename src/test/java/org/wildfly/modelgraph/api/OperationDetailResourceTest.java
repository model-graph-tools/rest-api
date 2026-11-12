package org.wildfly.modelgraph.api;

import java.util.List;
import java.util.Optional;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class OperationDetailResourceTest {

    @InjectMock
    ModelGraphRepository repository;

    @Test
    void detailReturnsOperationWithAllFields() {
        Mockito.when(repository.operationDetail("/subsystem=datasources/data-source=*", "add"))
                .thenReturn(Optional.of(new OperationDetailResponse(
                        "add", "Adds a data source", "stable", false, false, false,
                        null,
                        "data-source", "/subsystem=datasources/data-source=*",
                        List.of(new OperationDetailResponse.Parameter(
                                "connection-url", "STRING", "The JDBC connection URL",
                                true, false, null, null)),
                        null)));

        given()
                .when().get("/api/operation?resource=/subsystem=datasources/data-source=*&name=add")
                .then()
                .statusCode(200)
                .body("name", is("add"))
                .body("description", is("Adds a data source"))
                .body("stability", is("stable"))
                .body("readOnly", is(false))
                .body("runtimeOnly", is(false))
                .body("global", is(false))
                .body("deprecation", nullValue())
                .body("resourceName", is("data-source"))
                .body("resourceAddress", is("/subsystem=datasources/data-source=*"))
                .body("parameters", hasSize(1))
                .body("parameters[0].name", is("connection-url"))
                .body("parameters[0].type", is("STRING"))
                .body("parameters[0].description", is("The JDBC connection URL"))
                .body("parameters[0].required", is(true))
                .body("returnValue", nullValue());
    }

    @Test
    void detailWithDeprecationAndReturnValue() {
        Mockito.when(repository.operationDetail("/subsystem=io", "read-resource"))
                .thenReturn(Optional.of(new OperationDetailResponse(
                        "read-resource", "Reads a resource", "stable", true, false, true,
                        new OperationDetailResponse.Deprecation("30.0.0", "Use read-resource-description instead"),
                        "io", "/subsystem=io",
                        List.of(),
                        new OperationDetailResponse.ReturnValue("OBJECT", "The resource model"))));

        given()
                .when().get("/api/operation?resource=/subsystem=io&name=read-resource")
                .then()
                .statusCode(200)
                .body("readOnly", is(true))
                .body("global", is(true))
                .body("deprecation.since", is("30.0.0"))
                .body("deprecation.reason", is("Use read-resource-description instead"))
                .body("parameters", empty())
                .body("returnValue.type", is("OBJECT"))
                .body("returnValue.description", is("The resource model"));
    }

    @Test
    void detailWithParameterDefaults() {
        Mockito.when(repository.operationDetail("/subsystem=test", "test-op"))
                .thenReturn(Optional.of(new OperationDetailResponse(
                        "test-op", null, "stable", false, true, false,
                        null, "test", "/subsystem=test",
                        List.of(new OperationDetailResponse.Parameter(
                                "timeout", "INT", "Timeout in seconds",
                                false, true, "30",
                                List.of("10", "30", "60"))),
                        null)));

        given()
                .when().get("/api/operation?resource=/subsystem=test&name=test-op")
                .then()
                .statusCode(200)
                .body("runtimeOnly", is(true))
                .body("parameters[0].required", is(false))
                .body("parameters[0].nillable", is(true))
                .body("parameters[0].defaultValue", is("30"))
                .body("parameters[0].allowedValues", hasSize(3));
    }

    @Test
    void detailWithUnknownOperationReturns404() {
        Mockito.when(repository.operationDetail("/subsystem=io", "nonexistent"))
                .thenReturn(Optional.empty());

        given()
                .when().get("/api/operation?resource=/subsystem=io&name=nonexistent")
                .then()
                .statusCode(404)
                .body("error", notNullValue())
                .body("resource", is("/subsystem=io"))
                .body("name", is("nonexistent"));
    }

    @Test
    void detailWithMissingResourceReturns400() {
        given()
                .when().get("/api/operation?name=add")
                .then()
                .statusCode(400)
                .body("error", notNullValue());
    }

    @Test
    void detailWithMissingNameReturns400() {
        given()
                .when().get("/api/operation?resource=/subsystem=io")
                .then()
                .statusCode(400)
                .body("error", notNullValue());
    }
}
