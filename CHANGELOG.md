# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/model-graph-tools/rest-api/compare/v0.2.4...HEAD
[0.2.4]: https://github.com/model-graph-tools/rest-api/compare/v0.2.3...v0.2.4
[0.2.3]: https://github.com/model-graph-tools/rest-api/compare/v0.2.2...v0.2.3
[0.2.2]: https://github.com/model-graph-tools/rest-api/compare/v0.2.1...v0.2.2
[0.2.1]: https://github.com/model-graph-tools/rest-api/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/model-graph-tools/rest-api/compare/v0.1.3...v0.2.0
[0.1.3]: https://github.com/model-graph-tools/rest-api/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/model-graph-tools/rest-api/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/model-graph-tools/rest-api/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/model-graph-tools/rest-api/releases/tag/v0.1.0
