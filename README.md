# Model Graph REST API

A lightweight [Quarkus](https://quarkus.io/) REST API for querying the WildFly management model graph stored in Neo4j. This API is designed to be embedded in the [MGT](https://model-graph-tools.github.io/) Neo4j container image, providing typed search endpoints for the [halOP](https://github.com/hal/foundation) management console and other consumers.

## Overview

The MGT ecosystem indexes the WildFly management model into a Neo4j graph database. This REST API sits in front of that database and exposes purpose-built endpoints for:

- **Unified search** across resources, attributes, and capabilities
- **Health/ping** for service discovery
- **Version** information for client compatibility checks
- **Capability references** for cross-subsystem navigation
- **Resource details** for deep-linking

## Architecture

```
┌────────────────────────────────────────────────────────────┐
│                  MGT Container                             │
│                                                            │
│  ┌──────────┐     ┌──────────────┐     ┌───────────────┐   │
│  │  nginx   │────►│  REST API    │────►│    Neo4j      │   │
│  │  :7474   │     │  (Quarkus)   │     │  (Bolt :7687) │   │
│  │          │     │  :8080       │     │               │   │
│  └──────────┘     └──────────────┘     └───────────────┘   │
│       │                                                    │
│       ├── /api/*  → REST API                               │
│       └── /*      → Neo4j Browser                          │
└────────────────────────────────────────────────────────────┘
```

When deployed inside the MGT container, nginx proxies `/api/*` requests to the Quarkus process. The REST API connects to Neo4j via the Bolt protocol. No new ports are exposed — everything is reachable through the existing MGT HTTP port.

## Build

Requires Java 25.

```bash
# Development mode
./mvnw quarkus:dev

# Package (JVM)
./mvnw package

# Package (native image, requires GraalVM)
./mvnw package -Pnative

# Build + tests
./mvnw verify
```

## Development

Start an MGT Neo4j container for the WildFly version you want to work with:

```bash
mgt start 41
```

Then start the REST API in dev mode, pointing at the container's Bolt port:

```bash
./mvnw quarkus:dev -Dquarkus.neo4j.uri=bolt://localhost:6410
```

The API is available at http://localhost:8080. Health checks are at http://localhost:8080/q/health. The OpenAPI spec is at http://localhost:8080/q/openapi.

## Release

Releases are created with the `release.sh` script:

```bash
./release.sh 0.1.0
```

This bumps the POM version, commits, tags, and pushes to origin. The tag triggers the [release workflow](.github/workflows/release.yml), which builds native binaries for both `linux/amd64` and `linux/arm64` using GraalVM and uploads them as GitHub release assets.

## Container Integration

The REST API is not shipped as a standalone container. Instead, the native binaries from each release are downloaded by the [tooling](https://github.com/model-graph-tools/tooling) repo during the MGT container image build. The MGT container is a multi-arch image (`linux/amd64`, `linux/arm64`), and the correct binary is selected based on `TARGETARCH`.

Inside the container, nginx proxies `/api/*` to the Quarkus process on port 8080. No new ports are exposed — the API is reachable through the existing MGT HTTP port (e.g., `http://localhost:7410/api/search?q=pool`).

## Related Projects

- [model-graph-tools/analyzer](https://github.com/model-graph-tools/analyzer) — Java CLI that reads the WildFly model and writes it into Neo4j
- [model-graph-tools/tooling](https://github.com/model-graph-tools/tooling) — Rust CLI (`mgt`) that manages analysis pipelines and Neo4j containers
- [model-graph-tools/claude-plugin](https://github.com/model-graph-tools/claude-plugin) — MCP server and Claude Code plugin for AI-assisted model exploration
- [hal/foundation](https://github.com/hal/foundation) — halOP management console (primary consumer of this API)

## License

[Apache License, Version 2.0](LICENSE)
