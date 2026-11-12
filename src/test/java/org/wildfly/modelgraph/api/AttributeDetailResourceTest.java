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
class AttributeDetailResourceTest {

    @InjectMock
    ModelGraphRepository repository;

    @Test
    void detailReturnsAttributeWithAllFields() {
        Mockito.when(repository.attributeDetail("/subsystem=datasources/data-source=*", "connection-url"))
                .thenReturn(Optional.of(new AttributeDetailResponse(
                        "connection-url", "The JDBC driver connection URL", "STRING", "stable",
                        true, false, true, "read-write", "configuration", "no-services",
                        null, null, "jdbc:h2:mem:test",
                        List.of("jdbc:h2:mem:test", "jdbc:postgresql://localhost/test"),
                        null, false,
                        "data-source", "/subsystem=datasources/data-source=*",
                        null, List.of(), List.of(), List.of())));

        given()
                .when().get("/api/attribute?resource=/subsystem=datasources/data-source=*&name=connection-url")
                .then()
                .statusCode(200)
                .body("name", is("connection-url"))
                .body("description", is("The JDBC driver connection URL"))
                .body("type", is("STRING"))
                .body("stability", is("stable"))
                .body("required", is(true))
                .body("nillable", is(false))
                .body("expressionsAllowed", is(true))
                .body("accessType", is("read-write"))
                .body("storage", is("configuration"))
                .body("restartRequired", is("no-services"))
                .body("defaultValue", is("jdbc:h2:mem:test"))
                .body("allowedValues", hasSize(2))
                .body("deprecation", nullValue())
                .body("sensitive", is(false))
                .body("resourceName", is("data-source"))
                .body("resourceAddress", is("/subsystem=datasources/data-source=*"))
                .body("referencesCapability", nullValue());
    }

    @Test
    void detailWithDeprecationAndCapabilityRef() {
        Mockito.when(repository.attributeDetail("/subsystem=io/buffer-pool=*", "buffer-size"))
                .thenReturn(Optional.of(new AttributeDetailResponse(
                        "buffer-size", "Size of each buffer", "INT", "stable",
                        false, true, true, "read-write", "configuration", "all-services",
                        "pool", "BYTES", "1024", null,
                        new AttributeDetailResponse.Deprecation("29.0.0", "Use pool-size instead"),
                        false,
                        "buffer-pool", "/subsystem=io/buffer-pool=*",
                        "org.wildfly.io.buffer-pool",
                        List.of(new AttributeDetailResponse.AttributeRef("pool-size")),
                        List.of(new AttributeDetailResponse.AttributeRef("max-size")),
                        List.of())));

        given()
                .when().get("/api/attribute?resource=/subsystem=io/buffer-pool=*&name=buffer-size")
                .then()
                .statusCode(200)
                .body("attributeGroup", is("pool"))
                .body("unit", is("BYTES"))
                .body("deprecation.since", is("29.0.0"))
                .body("deprecation.reason", is("Use pool-size instead"))
                .body("referencesCapability", is("org.wildfly.io.buffer-pool"))
                .body("requires", hasSize(1))
                .body("requires[0].name", is("pool-size"))
                .body("alternatives", hasSize(1))
                .body("alternatives[0].name", is("max-size"));
    }

    @Test
    void detailWithSubAttributes() {
        Mockito.when(repository.attributeDetail("/subsystem=test", "complex-attr"))
                .thenReturn(Optional.of(new AttributeDetailResponse(
                        "complex-attr", null, "OBJECT", "stable",
                        false, false, false, "read-write", "configuration", "no-services",
                        null, null, null, null, null, false,
                        "test", "/subsystem=test", null,
                        List.of(), List.of(),
                        List.of(new AttributeDetailResponse.SubAttribute("sub1", "STRING"),
                                new AttributeDetailResponse.SubAttribute("sub2", "INT")))));

        given()
                .when().get("/api/attribute?resource=/subsystem=test&name=complex-attr")
                .then()
                .statusCode(200)
                .body("subAttributes", hasSize(2))
                .body("subAttributes[0].name", is("sub1"))
                .body("subAttributes[0].type", is("STRING"));
    }

    @Test
    void detailWithUnknownAttributeReturns404() {
        Mockito.when(repository.attributeDetail("/subsystem=io", "nonexistent"))
                .thenReturn(Optional.empty());

        given()
                .when().get("/api/attribute?resource=/subsystem=io&name=nonexistent")
                .then()
                .statusCode(404)
                .body("error", notNullValue())
                .body("resource", is("/subsystem=io"))
                .body("name", is("nonexistent"));
    }

    @Test
    void detailWithMissingResourceReturns400() {
        given()
                .when().get("/api/attribute?name=foo")
                .then()
                .statusCode(400)
                .body("error", notNullValue());
    }

    @Test
    void detailWithMissingNameReturns400() {
        given()
                .when().get("/api/attribute?resource=/subsystem=io")
                .then()
                .statusCode(400)
                .body("error", notNullValue());
    }
}
