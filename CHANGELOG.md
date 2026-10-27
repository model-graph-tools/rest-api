# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.3.2] - 2026-10-27

### Fixed
- Fix catalog API to always include zero-count values in facet count maps for type, stability, dataType, accessType, storage, restart, and boolean attribute facets, preventing client-side undefined key errors

## [0.3.1] - 2026-10-26

### Fixed
- Fix native image crash caused by Neo4j Java driver 6.3.0 re-introduced via dependabot (driver pinned back to 6.2.1 until quarkus-neo4j handles the bolt codec SPI in native image)

## [0.3.0] - 2026-10-26

### Added
- Add `GET /api/catalog` endpoint — faceted search across resources, attributes, operations, and capabilities with paginated results and facet counts
- Support 30+ filter dimensions: type, stability, deprecated, scope, keyword search, sort/order, and type-specific facets for resources (singleton, declaresCap, definesAttrGroups), attributes (dataType, accessType, storage, restart, required, nillable, expressions, sensitive, hasAllowed, unit, hasRelationships, refsCap), and operations (readOnly, runtimeOnly, global, hasParams, hasReturnValue)
- Add sealed `Properties` interface with `ResourceProperties`, `AttributeProperties`, and `OperationProperties` subtypes for type-specific result fields
- Add `singleton` field to `/api/search` results indicating wildcard vs singleton resource status
- Add OpenAPI annotations to catalog endpoint documenting all query parameters
- Include stability level in search results and capability references
- Add glossary (`GLOSSARY.md`) and API contract (`docs/catalog-api-contract.md`)
- Add ADR 0001: drop `deprecated=since:<version>` filter in favor of simple yes/no

### Changed
- **BREAKING:** Change `/api/search` type values from PascalCase to lowercase (`Resource` → `resource`, `Attribute` → `attribute`, `Operation` → `operation`, `Capability` → `capability`)
- Update default MGT test image to WildFly 42
- Bump `quarkus-neo4j` from 6.8.0 to 6.8.1

### Fixed
- Enforce cross-type AND semantics in catalog (e.g., `type=operation&dataType=string` correctly returns zero results)
- Strip invalid type parameter values instead of returning 500 errors
- Cap catalog page size at 500 to prevent unbounded result sets

## [0.2.9] - 2026-10-12

### Fixed
- Match capabilities by individual segment instead of full dotted name, so searches like "batch", "datasource", or "elytron" now find capabilities such as `org.wildfly.batch.configuration` or `org.wildfly.data-source`

## [0.2.8] - 2026-10-09

### Fixed
- Escape forward slashes in search queries to prevent Lucene parse errors
- Return empty results instead of 500 error for malformed search queries

## [0.2.7] - 2026-10-08

### Fixed
- Fix native image crash with Neo4j 2026.09 by downgrading Neo4j Java driver to 6.2.1 (driver 6.3.0 introduced Property Encryption SPI that fails in GraalVM native image)

## [0.2.6] - 2026-10-08

### Added
- Add unit tests for `escapeAndWildcard()` covering edge cases (leading/trailing delimiters, consecutive delimiters, mixed delimiters, Lucene operators)
- Add integration tests for hyphenated resources, attributes, operations, dotted capabilities, and partial terms

### Changed
- Bump Quarkus to 3.40.1
- Bump Quarkus Neo4j extension to 6.8.0
- Bump jboss-parent to 54
- Bump Maven wrapper to 3.10.0
- Bump softprops/action-gh-release to v3

### Fixed
- Fix search for hyphenated, dotted, and special-character terms by splitting on delimiters and joining with Lucene AND operators
- Preserve dots in search terms to match Neo4j's standard analyzer tokenization (e.g., `org.wildfly.io.buffer-pool` now returns correct results)
- Escape Lucene special characters and neutralize boolean operators (AND/OR/NOT) appearing as literal parts of names

## [0.2.5] - 2026-09-24

### Added
- Automate changelog updates in release script using `keepachangelog-maven-plugin`
- Populate GitHub release notes from changelog via `changelog-reader-action`

### Changed
- Include declaring resources in capability search results via `providedBy` field

## [0.2.4] - 2026-09-23

### Fixed
- Exclude global operations from search results using `node.global` property
- Use Maven wrapper in release script

## [0.2.3] - 2026-09-23

### Fixed
- Use single `mgt_search` fulltext index for all search queries

## [0.2.2] - 2026-09-23

### Added
- Add operation search using full-text indices

## [0.2.1] - 2026-09-23

### Fixed
- Use Maven resource filtering to bake build info into native image
- Replace deprecated GitHub Actions in release workflow

## [0.2.0] - 2026-09-23

### Added
- Add `/api/identity` endpoint returning feature pack metadata

### Changed
- Rework `/api/version` to return API build info instead of WildFly version

## [0.1.3] - 2026-09-23

### Fixed
- Add `@RegisterForReflection` to all record types for native image serialization

## [0.1.2] - 2026-09-23

### Changed
- Bump Quarkus to 3.39.4 and update build dependencies

## [0.1.1] - 2026-09-23

### Fixed
- Wrap search query `UNION ALL` parts in `CALL` subqueries for Neo4j 5+ compatibility

## [0.1.0] - 2026-09-23

### Added
- REST API endpoints for search, capability references, and version
- Unified search across resources, attributes, and capabilities
- `/api/capability/{name}/references` endpoint
- `/api/version` endpoint
- Integration tests using MGT Testcontainers
- Release script and CI workflows (verify + release)
- Multi-arch native image builds (amd64 + arm64)

[Unreleased]: https://github.com/model-graph-tools/rest-api/compare/v0.3.2...HEAD
[0.3.2]: https://github.com/model-graph-tools/rest-api/compare/v0.3.1...v0.3.2
[0.3.1]: https://github.com/model-graph-tools/rest-api/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/model-graph-tools/rest-api/compare/v0.2.9...v0.3.0
[0.2.9]: https://github.com/model-graph-tools/rest-api/compare/v0.2.8...v0.2.9
[0.2.8]: https://github.com/model-graph-tools/rest-api/compare/v0.2.7...v0.2.8
[0.2.7]: https://github.com/model-graph-tools/rest-api/compare/v0.2.6...v0.2.7
[0.2.6]: https://github.com/model-graph-tools/rest-api/compare/v0.2.5...v0.2.6
[0.2.5]: https://github.com/model-graph-tools/rest-api/compare/v0.2.4...v0.2.5
[0.2.4]: https://github.com/model-graph-tools/rest-api/compare/v0.2.3...v0.2.4
[0.2.3]: https://github.com/model-graph-tools/rest-api/compare/v0.2.2...v0.2.3
[0.2.2]: https://github.com/model-graph-tools/rest-api/compare/v0.2.1...v0.2.2
[0.2.1]: https://github.com/model-graph-tools/rest-api/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/model-graph-tools/rest-api/compare/v0.1.3...v0.2.0
[0.1.3]: https://github.com/model-graph-tools/rest-api/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/model-graph-tools/rest-api/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/model-graph-tools/rest-api/compare/v0.1.0...v0.1.1
