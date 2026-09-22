package org.wildfly.modelgraph.api;

import java.time.Duration;
import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class MgtContainerResource implements QuarkusTestResourceLifecycleManager {

    private static final String DEFAULT_IMAGE = "quay.io/modelgraphtools/model:41.0.1";
    private static final int BOLT_PORT = 7687;

    private GenericContainer<?> container;

    @Override
    public Map<String, String> start() {
        String image = System.getProperty("mgt.image", DEFAULT_IMAGE);
        container = new GenericContainer<>(image)
                .withExposedPorts(BOLT_PORT)
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));
        container.start();
        String boltUrl = "bolt://" + container.getHost() + ":" + container.getMappedPort(BOLT_PORT);
        return Map.of("quarkus.neo4j.uri", boltUrl);
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }
}
