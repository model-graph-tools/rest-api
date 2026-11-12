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
class CapabilityResourceTest {

    @InjectMock
    ModelGraphRepository repository;

    // ------------------------------------------------------ /references (existing)

    @Test
    void referencesReturnsMatchingResources() {
        Mockito.when(repository.capabilityReferences("org.wildfly.network.socket-binding"))
                .thenReturn(List.of(
                        new CapabilityReference("socket-binding", "default", "http-listener",
                                "/subsystem=undertow/server=*/http-listener=*", "default"),
                        new CapabilityReference("socket-binding", "default", "https-listener",
                                "/subsystem=undertow/server=*/https-listener=*", "default")));

        given()
                .when().get("/api/capability/org.wildfly.network.socket-binding/references")
                .then()
                .statusCode(200)
                .body("capability", is("org.wildfly.network.socket-binding"))
                .body("references", hasSize(2))
                .body("references[0].attributeName", is("socket-binding"))
                .body("references[0].resourceName", is("http-listener"))
                .body("references[0].attributeStability", is("default"))
                .body("references[0].resourceAddress", is("/subsystem=undertow/server=*/http-listener=*"))
                .body("references[0].resourceStability", is("default"));
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

    // ------------------------------------------------------ /detail (new)

    @Test
    void detailReturnsCapabilityWithDeclaredByAndReferencedBy() {
        Mockito.when(repository.capabilityDetail("org.wildfly.data-source"))
                .thenReturn(Optional.of(new CapabilityDetailResponse(
                        "org.wildfly.data-source",
                        "stable",
                        List.of(new CapabilityDetailResponse.DeclaredBy("data-source",
                                "/subsystem=datasources/data-source=*")),
                        List.of(new CapabilityReference("datasource", "stable", "mail-session",
                                "/subsystem=mail/mail-session=*", "stable")))));

        given()
                .when().get("/api/capability/org.wildfly.data-source")
                .then()
                .statusCode(200)
                .body("name", is("org.wildfly.data-source"))
                .body("stability", is("stable"))
                .body("declaredBy", hasSize(1))
                .body("declaredBy[0].resourceName", is("data-source"))
                .body("declaredBy[0].resourceAddress", is("/subsystem=datasources/data-source=*"))
                .body("referencedBy", hasSize(1))
                .body("referencedBy[0].attributeName", is("datasource"))
                .body("referencedBy[0].resourceAddress", is("/subsystem=mail/mail-session=*"));
    }

    @Test
    void detailWithUnknownCapabilityReturns404() {
        Mockito.when(repository.capabilityDetail("org.wildfly.nonexistent"))
                .thenReturn(Optional.empty());

        given()
                .when().get("/api/capability/org.wildfly.nonexistent")
                .then()
                .statusCode(404)
                .body("error", notNullValue())
                .body("name", is("org.wildfly.nonexistent"));
    }

    @Test
    void detailWithEmptyRelationshipsReturnsEmptyLists() {
        Mockito.when(repository.capabilityDetail("org.wildfly.lonely"))
                .thenReturn(Optional.of(new CapabilityDetailResponse(
                        "org.wildfly.lonely", "stable", List.of(), List.of())));

        given()
                .when().get("/api/capability/org.wildfly.lonely")
                .then()
                .statusCode(200)
                .body("name", is("org.wildfly.lonely"))
                .body("declaredBy", empty())
                .body("referencedBy", empty());
    }
}
