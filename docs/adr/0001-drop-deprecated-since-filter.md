# ADR 0001: Drop `deprecated=since:<version>` filter

## Status

Accepted

## Context

The catalog API contract originally specified a `deprecated=since:<version>` filter
that would let users find elements deprecated in a specific version. This required
a `/api/versions` endpoint to provide the dropdown values.

Investigation revealed that `Version` nodes in the graph use **management model
versions** (e.g., `1.0.0`, `6.1.0`, `24.0.0`) — a scheme completely independent
from WildFly release versions (e.g., 25, 38, 42). There is no mapping between them.
The management model version sets also differ across WildFly releases (nodes
appear/disappear as deprecations change).

## Decision

Drop the `since:<version>` variant. The `deprecated` filter supports only `yes`/`no`.
The `/api/versions` endpoint is removed entirely.

## Consequences

- Simpler API — no need to expose an obscure internal versioning scheme to halOP users
- The client does not need a versions dropdown for the deprecated facet
- If version-specific deprecation filtering is needed later, it can be added once the
  relationship between WildFly releases and management model versions is better understood
