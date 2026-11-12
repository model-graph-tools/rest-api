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
class ResourceDetailResourceTest {

    @InjectMock
    ModelGraphRepository repository;

    @Test
    void detailReturnsResourceWithAllFields() {
        Mockito.when(repository.resourceDetail("/subsystem=io/buffer-pool=*"))
                .thenReturn(Optional.of(new ResourceDetailResponse(
                        "buffer-pool",
                        "/subsystem=io/buffer-pool=*",
                        "A pool of buffers",
                        "stable",
                        false,
                        null,
                        "/subsystem=io",
                        List.of(new ResourceDetailResponse.ChildResource("worker", "/subsystem=io/worker=*")),
                        List.of(new ResourceDetailResponse.AttributeSummary("buffer-size", "INT", "stable",
                                "read-write", "configuration", false)),
                        List.of(new ResourceDetailResponse.OperationSummary("add", "stable", false, false, false,
                                false)),
                        List.of(new ResourceDetailResponse.CapabilitySummary("org.wildfly.io.buffer-pool")))));

        given()
                .when().get("/api/resource?address=/subsystem=io/buffer-pool=*")
                .then()
                .statusCode(200)
                .body("name", is("buffer-pool"))
                .body("address", is("/subsystem=io/buffer-pool=*"))
                .body("description", is("A pool of buffers"))
                .body("stability", is("stable"))
                .body("singleton", is(false))
                .body("deprecation", nullValue())
                .body("parentAddress", is("/subsystem=io"))
                .body("childResources", hasSize(1))
                .body("childResources[0].name", is("worker"))
                .body("childResources[0].address", is("/subsystem=io/worker=*"))
                .body("attributes", hasSize(1))
                .body("attributes[0].name", is("buffer-size"))
                .body("attributes[0].type", is("INT"))
                .body("operations", hasSize(1))
                .body("operations[0].name", is("add"))
                .body("capabilities", hasSize(1))
                .body("capabilities[0].name", is("org.wildfly.io.buffer-pool"));
    }

    @Test
    void detailWithDeprecationReturnsDeprecationObject() {
        Mockito.when(repository.resourceDetail("/subsystem=old"))
                .thenReturn(Optional.of(new ResourceDetailResponse(
                        "old", "/subsystem=old", "Deprecated subsystem", "stable", false,
                        new ResourceDetailResponse.Deprecation("29.0.0", "Use new-subsystem instead"),
                        null, List.of(), List.of(), List.of(), List.of())));

        given()
                .when().get("/api/resource?address=/subsystem=old")
                .then()
                .statusCode(200)
                .body("deprecation.since", is("29.0.0"))
                .body("deprecation.reason", is("Use new-subsystem instead"));
    }

    @Test
    void detailWithUnknownAddressReturns404() {
        Mockito.when(repository.resourceDetail("/subsystem=nonexistent"))
                .thenReturn(Optional.empty());

        given()
                .when().get("/api/resource?address=/subsystem=nonexistent")
                .then()
                .statusCode(404)
                .body("error", notNullValue())
                .body("address", is("/subsystem=nonexistent"));
    }

    @Test
    void detailWithMissingAddressReturns400() {
        given()
                .when().get("/api/resource")
                .then()
                .statusCode(400)
                .body("error", notNullValue());
    }

    @Test
    void detailWithEmptyRelationshipsReturnsEmptyLists() {
        Mockito.when(repository.resourceDetail("/subsystem=lonely"))
                .thenReturn(Optional.of(new ResourceDetailResponse(
                        "lonely", "/subsystem=lonely", null, "stable", false,
                        null, null, List.of(), List.of(), List.of(), List.of())));

        given()
                .when().get("/api/resource?address=/subsystem=lonely")
                .then()
                .statusCode(200)
                .body("childResources", empty())
                .body("attributes", empty())
                .body("operations", empty())
                .body("capabilities", empty());
    }
}
