package org.wildfly.modelgraph.api;

import java.util.List;

import io.quarkus.test.junit.QuarkusTestProfile;

public class IntegrationTestProfile implements QuarkusTestProfile {

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(MgtContainerResource.class));
    }
}
