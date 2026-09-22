# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Quarkus REST API for querying the WildFly management model graph stored in Neo4j. It is part of the [Model Graph Tools](https://model-graph-tools.github.io/) ecosystem and serves as the backend for search and navigation features in the [halOP management console](https://github.com/hal/foundation).

The API is designed to be embedded in the existing MGT Neo4j container image (which already runs nginx + Neo4j as a multi-process container). See [issue #4](https://github.com/model-graph-tools/rest-api/issues/4) for the container packaging design.

## Build Commands

```bash
./mvnw quarkus:dev                                           # Dev mode
./mvnw quarkus:dev -Dquarkus.neo4j.uri=bolt://localhost:6410 # Dev mode against MGT container
./mvnw package                                               # Package (JVM)
./mvnw package -Pnative                                      # Package (native image)
./mvnw verify                                                # Build + tests
./mvnw verify -Pintegration-test                             # Build + tests + integration tests
./mvnw verify -Pintegration-test -Dmgt.image=quay.io/modelgraphtools/model:42.0.0  # Override MGT image
```

## Development Setup

Start an MGT Neo4j container, then run the API in dev mode pointing at its Bolt port:

```bash
mgt start 41
./mvnw quarkus:dev -Dquarkus.neo4j.uri=bolt://localhost:6410
```

Use `mgt ps --json` to find the Bolt and HTTP ports for running containers.

## Architecture

### Endpoints

The API exposes ~5 endpoints (see issues [#1](https://github.com/model-graph-tools/rest-api/issues/1), [#2](https://github.com/model-graph-tools/rest-api/issues/2), [#3](https://github.com/model-graph-tools/rest-api/issues/3) for specs):

- `GET /api/search?q=<term>&limit=<n>` — unified search across resources, attributes, and capabilities
- `GET /api/capability/{name}/references` — resources referencing a capability
- `GET /api/version` — WildFly version the graph was built for
- Health check via Quarkus SmallRye Health (`/q/health`)

### Neo4j Graph Schema

The MGT Neo4j database contains these node types and relationships:

**Nodes:** `Resource` (name, address, description, stability), `Attribute` (name, description, type, required, nillable, expressions-allowed, access-type, storage, stability), `Operation` (name), `Parameter` (name), `Capability` (name, stability), `Constraint`, `Version`

**Key relationships:** `Resource -[HAS_ATTRIBUTE]-> Attribute`, `Resource -[DECLARES_CAPABILITY]-> Capability`, `Resource -[CHILD_OF]-> Resource`, `Resource -[PROVIDES]-> Operation`, `Attribute -[REFERENCES_CAPABILITY]-> Capability`, `Attribute -[REQUIRES]-> Attribute`, `Attribute -[ALTERNATIVE]-> Attribute`

**Indexes:** RANGE indexes on `name` for Resource, Attribute, Capability, Operation, Parameter. A full-text index (`mgt_search`) across Resource/Attribute/Capability name+description is planned ([analyzer#1](https://github.com/model-graph-tools/analyzer/issues/1)).

### How halOP Uses This API

halOP converts search results into navigation by passing the `address` field (e.g., `/subsystem=io/buffer-pool=*`) to `AddressTemplate.ofTrusted(address)` and then `RouteRegistry.goTo(template)`. The leading `/` in addresses is handled transparently by `AddressTemplate`. See [hal/foundation#288](https://github.com/hal/foundation/issues/288) sections 4-5 for the full routing design.

Search results should exclude deployment-scoped resources (`NOT r.address STARTS WITH '/deployment'`) since they aren't navigable in halOP's configuration UI.

### Release and Container Integration

Releases are created via `release.sh`, which bumps the POM version, commits, tags, and pushes. The tag triggers the GitHub release workflow (`.github/workflows/release.yml`), which builds multi-arch native binaries:

- **Build matrix:** `ubuntu-latest` (amd64) + `ubuntu-latest-arm64-small` (arm64), both using GraalVM
- **Release assets:** `rest-api-<version>-linux-amd64` and `rest-api-<version>-linux-arm64`
- **Verify workflow:** `.github/workflows/verify.yml` runs `mvn verify` on pushes to main and PRs

The REST API is embedded in the existing MGT Neo4j container image (not shipped as a standalone container). The [tooling](https://github.com/model-graph-tools/tooling) repo's container build downloads the correct native binary from the GitHub release based on `TARGETARCH`. The MGT container is a multi-arch image (`linux/amd64`, `linux/arm64`).

Inside the container, nginx proxies `/api/*` to the Quarkus process on port 8080 internally. No new ports are exposed to the user — the API is reachable via the existing MGT HTTP port (e.g., `http://localhost:7410/api/search?q=pool`). See [issue #4](https://github.com/model-graph-tools/rest-api/issues/4).

## Related Projects

- [model-graph-tools/analyzer](https://github.com/model-graph-tools/analyzer) — Java CLI that populates the Neo4j graph
- [model-graph-tools/tooling](https://github.com/model-graph-tools/tooling) — Rust CLI (`mgt`) that manages containers and builds images
- [model-graph-tools/claude-plugin](https://github.com/model-graph-tools/claude-plugin) — MCP server for AI-assisted model exploration
- [hal/foundation](https://github.com/hal/foundation) — halOP console (primary consumer of this API)

## Design Decisions

- **Java/Quarkus** chosen over Rust and TypeScript for driver maturity (official Neo4j Java driver), language alignment with the analyzer and halOP, and the Quarkus neo4j extension's out-of-the-box CDI/health/native-image support.
- **Embedded in MGT container** (not sidecar) because the container already runs nginx + Neo4j. This avoids new ports, solves CORS via same-origin nginx proxying, and requires zero UX changes to `mgt start/stop/ps`.
- **Independent from the MCP server** — both query Neo4j directly. The MCP server serves AI agents, this API serves halOP. No coupling between them.
