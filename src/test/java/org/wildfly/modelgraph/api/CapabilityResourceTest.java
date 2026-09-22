package org.wildfly.modelgraph.api;

import java.util.List;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class CapabilityResourceTest {

    @InjectMock
    ModelGraphRepository repository;

    @Test
    void referencesReturnsMatchingResources() {
        Mockito.when(repository.capabilityReferences("org.wildfly.network.socket-binding"))
                .thenReturn(List.of(
                        new CapabilityReference("socket-binding", "http-listener",
                                "/subsystem=undertow/server=*/http-listener=*"),
                        new CapabilityReference("socket-binding", "https-listener",
                                "/subsystem=undertow/server=*/https-listener=*")));

        given()
                .when().get("/api/capability/org.wildfly.network.socket-binding/references")
                .then()
                .statusCode(200)
                .body("capability", is("org.wildfly.network.socket-binding"))
                .body("references", hasSize(2))
                .body("references[0].attributeName", is("socket-binding"))
                .body("references[0].resourceName", is("http-listener"))
                .body("references[0].resourceAddress", is("/subsystem=undertow/server=*/http-listener=*"));
    }

    @Test
    void referencesWithNoMatchesReturnsEmptyList() {
        Mockito.when(repository.capabilityReferences("org.wildfly.nonexistent"))
                .thenReturn(List.of());

        given()
                .when().get("/api/capability/org.wildfly.nonexistent/references")
                .then()
                .statusCode(200)
                .body("capability", is("org.wildfly.nonexistent"))
                .body("references", hasSize(0));
    }
}
