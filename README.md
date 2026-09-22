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
│  ┌──────────┐     ┌──────────────┐     ┌───────────────┐  │
│  │  nginx   │────►│  REST API    │────►│    Neo4j      │  │
│  │  :7474   │     │  (Quarkus)   │     │  (Bolt :7687) │  │
│  │          │     │  :8080       │     │               │  │
│  └──────────┘     └──────────────┘     └───────────────┘  │
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

# Package (native image)
./mvnw package -Pnative
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

## Related Projects

- [model-graph-tools/analyzer](https://github.com/model-graph-tools/analyzer) — Java CLI that reads the WildFly model and writes it into Neo4j
- [model-graph-tools/tooling](https://github.com/model-graph-tools/tooling) — Rust CLI (`mgt`) that manages analysis pipelines and Neo4j containers
- [model-graph-tools/claude-plugin](https://github.com/model-graph-tools/claude-plugin) — MCP server and Claude Code plugin for AI-assisted model exploration
- [hal/foundation](https://github.com/hal/foundation) — halOP management console (primary consumer of this API)

## License

[Apache License, Version 2.0](LICENSE)
