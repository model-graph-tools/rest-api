# Catalog API Contract

API contract for the Model Catalog endpoint. This document is self-contained — it has
everything needed to implement the endpoint in this codebase without referencing the
halOP spec.

## Context

halOP (the HAL management console) is building a faceted search page called
**Model Catalog** that exposes the full MGT data surface. This endpoint provides
the backend: paginated results with facet counts in a single response.

## Endpoint

### `GET /api/catalog`

Faceted search across resources, attributes, operations, and capabilities.

All query parameters are optional. When none are provided, the endpoint returns all
items (paginated) with full facet counts.

---

## `GET /api/catalog` — Request Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `q` | string | Fulltext search on `name` only (uses `mgt_search` fulltext index) |
| `type` | string (multi) | Node type: `resource`, `attribute`, `operation`, `capability` |
| `stability` | string (multi) | Stability level: `default`, `community`, `preview`, `experimental` |
| `deprecated` | string | `yes` or `no` |
| `scope` | string | Resource address prefix — subtree filter for all types |
| `singleton` | boolean | Resource: singleton flag |
| `declaresCap` | string | Resource: `yes`, `no`, or `contains:<text>` |
| `definesAttrGroups` | boolean | Resource: defines attribute groups |
| `dataType` | string (multi) | Attribute: `string`, `boolean`, `int`, `long`, `object`, `list`, `double`, `bytes`, `complex` |
| `accessType` | string (multi) | Attribute: `read-write`, `read-only`, `metric` |
| `storage` | string (multi) | Attribute: `configuration`, `runtime` |
| `restart` | string (multi) | Attribute: `all-services`, `no-services`, `resource-services`, `jvm` |
| `required` | boolean | Attribute: required flag |
| `nillable` | boolean | Attribute: nillable flag |
| `expressions` | boolean | Attribute: expressions allowed |
| `sensitive` | boolean | Attribute: `IS_SENSITIVE` relationship |
| `hasAllowed` | boolean | Attribute: has allowed values |
| `unit` | string (multi) | Attribute: `milliseconds`, `bytes`, `seconds`, `per_second`, `nanoseconds`, `minutes`, `percentage`, `days` |
| `hasRelationships` | boolean | Attribute: has `REQUIRES` or `ALTERNATIVE` dependencies |
| `refsCap` | string | Attribute: `yes`, `no`, or `contains:<text>` |
| `readOnly` | boolean | Operation: read-only flag |
| `runtimeOnly` | boolean | Operation: runtime-only flag |
| `global` | boolean | Operation: global flag |
| `hasParams` | boolean | Operation: has parameters |
| `hasReturnValue` | boolean | Operation: has return value |
| `sort` | string | Sort field: `name` (default), `type`, `address`, `stability` |
| `order` | string | Sort direction: `asc` (default), `desc` |
| `limit` | int | Page size (default 50) |
| `offset` | int | Pagination offset (default 0) |

### Multi-value parameters

Comma-separated values (e.g., `type=resource,attribute`).

**Semantics:** OR within a facet, AND across facets.

- `type=resource,attribute` -> Resource OR Attribute
- `type=attribute` + `accessType=read-write` -> Attribute AND read-write

### Type-specific facets under multi-type selection

When multiple types are selected (e.g., `type=attribute,operation`) and a type-specific
facet is applied (e.g., `required=yes`), AND semantics apply. Items that cannot satisfy
the predicate are excluded.

### Filter validation

Inapplicable filters are silently ignored. For example, `type=operation&dataType=string`
returns zero results (no operations have a dataType) rather than a 400 error.

### Scope filter

The `scope` parameter is an address prefix. It applies to all types:
- **Resources:** `address STARTS WITH scope`
- **Attributes/Operations:** parent resource address starts with scope
- **Capabilities:** declaring resource address starts with scope

### Deprecated filter

Two modes:
- `deprecated=yes` — only deprecated elements
- `deprecated=no` — only non-deprecated elements

### Capability filters

Both `declaresCap` and `refsCap` support three modes:
- `yes` — has any capability relationship
- `no` — has no capability relationship
- `contains:<text>` — capability name contains substring

### Data type — COMPLEX

An attribute with `CONSISTS_OF` sub-attributes is typed as `complex` regardless of its
declared type. This is derived in the Cypher query using
`CASE WHEN exists((a)-[:CONSISTS_OF]->()) THEN 'complex' ELSE toLower(a.type) END`.

---

## `GET /api/catalog` — Response

```json
{
  "results": [
    {
      "type": "attribute",
      "name": "connection-url",
      "description": "The JDBC driver connection URL",
      "address": "/subsystem=datasources/data-source=*/connection-url",
      "resourceAddress": "/subsystem=datasources/data-source=*",
      "stability": "default",
      "deprecated": false,
      "properties": {
        "dataType": "string",
        "accessType": "read-write",
        "storage": "configuration",
        "restart": "all-services",
        "required": false,
        "nillable": true,
        "expressions": true,
        "sensitive": false,
        "unit": null
      }
    }
  ],
  "facets": {
    "type": { "resource": 42, "attribute": 128, "operation": 38, "capability": 5 },
    "stability": { "default": 200, "community": 8, "preview": 3, "experimental": 2 },
    "deprecated": { "yes": 15, "no": 198 },
    "dataType": { "string": 55, "boolean": 30, "int": 20, "long": 12, "complex": 15 },
    "accessType": { "read-write": 80, "read-only": 35, "metric": 13 },
    "storage": { "configuration": 90, "runtime": 38 },
    "restart": { "all-services": 50, "no-services": 25, "resource-services": 20, "jvm": 2 },
    "required": { "yes": 30, "no": 98 },
    "nillable": { "yes": 100, "no": 28 },
    "expressions": { "yes": 95, "no": 33 },
    "sensitive": { "yes": 12, "no": 116 },
    "hasAllowed": { "yes": 18, "no": 110 },
    "unit": { "milliseconds": 8, "bytes": 5, "seconds": 3 },
    "definesAttrGroups": { "yes": 40, "no": 88 },
    "hasRelationships": { "yes": 10, "no": 118 },
    "refsCap": { "yes": 8, "no": 120 },
    "singleton": { "yes": 20, "no": 22 },
    "declaresCap": { "yes": 15, "no": 27 },
    "readOnly": { "yes": 10, "no": 28 },
    "runtimeOnly": { "yes": 8, "no": 30 },
    "global": { "yes": 5, "no": 33 },
    "hasParams": { "yes": 25, "no": 13 },
    "hasReturnValue": { "yes": 15, "no": 23 }
  },
  "total": 213,
  "limit": 50,
  "offset": 0
}
```

### Casing convention

All values are **lowercase** throughout the API:
- Query parameter values: `type=resource`, `dataType=string`, `stability=default`
- Response values: `"type": "attribute"`, `"dataType": "string"`, `"unit": "milliseconds"`
- Facet keys: `{ "resource": 42, "attribute": 128 }`
- JSON object keys remain **camelCase**: `resourceAddress`, `dataType`, `hasReturnValue`

This convention also applies to the existing `/api/search` endpoint (retrofit).

### Result fields

| Field | Type | Present for | Description |
|-------|------|-------------|-------------|
| `type` | string | all | `resource`, `attribute`, `operation`, `capability` |
| `name` | string | all | Element name |
| `description` | string? | all | May be null |
| `address` | string? | R, A, O | Full address. For attributes: includes attribute name segment. Null for some capabilities. |
| `resourceAddress` | string? | A, O | Parent resource address (without the attribute/operation name) |
| `stability` | string? | all | Stability level |
| `deprecated` | boolean | all | Whether the element is deprecated |
| `properties` | object? | R, A, O | Type-specific properties (see below) |

### Properties by type

**Attribute properties:**
`dataType`, `accessType`, `storage`, `restart`, `required`, `nillable`, `expressions`,
`sensitive`, `unit`

**Operation properties:**
`readOnly`, `runtimeOnly`, `global`, `hasParams`, `hasReturnValue`

**Resource properties:**
`singleton`, `declaresCap` (boolean), `definesAttrGroups`

**Capability:** No additional properties.

### Properties Java design

Implemented as a sealed interface `Properties` with typed subtypes:
`AttributeProperties`, `OperationProperties`, `ResourceProperties`. Serialized with
Jackson `@JsonTypeInfo` / `@JsonSubTypes` or `@JsonInclude(NON_NULL)`.

### Facet count semantics

Facet counts reflect the result set with all other active filters applied **but without
the facet's own filter**. This lets the UI show how many results each value would produce
if clicked. Values with zero count are included.

### Contextual facet omission

When a `type` filter is active, omit facets that don't apply to the selected types.
For example, with `type=operation`, omit attribute-specific facets (`dataType`,
`accessType`, `storage`, etc.) and resource-specific facets (`singleton`, `declaresCap`).
When no type filter is active or multiple types are selected, return all facets.

Omission is implemented in the repository layer — irrelevant facet queries are not
executed (not filtered after the fact).

### Facet-to-type mapping

Managed by a Java `enum CatalogType` with an `applicableFacets()` method. This enum
is the single source of truth for which facets apply to which types.

### Sort — null handling

When sorting by `address`, null values (some capabilities) sort last regardless of
sort direction.

---

## Implementation Notes

### Code organization

New classes (do not modify existing endpoints):
- `CatalogResource` — JAX-RS resource with OpenAPI annotations
- `CatalogRepository` — Neo4j queries, one per facet
- `CatalogResponse`, `CatalogResult` — response DTOs
- `AttributeProperties`, `OperationProperties`, `ResourceProperties` — sealed subtypes
- `CatalogFilter` — query parameter parsing
- `CatalogFacet` — facet count objects
- `CatalogType` — enum with facet-to-type mapping

### Cypher query structure

The endpoint assembles a Cypher query from the filter parameters. Two parts:

1. **Result query** — `MATCH` + `WHERE` from filters, `RETURN` result fields,
   `ORDER BY`, `SKIP offset LIMIT limit`
2. **Facet aggregation queries** — one per applicable facet, each applying all filters
   *except its own*, returning `value, count(*)` grouped by that facet

### Query strategy

Start with one Cypher query per facet (simple, correct, testable). Optimize to batched
`CASE`/conditional counting later if performance warrants it.

### OpenAPI

Add `@Operation`, `@Parameter`, and `@Schema` annotations to `CatalogResource` for
usable generated documentation (30+ query params need descriptions and examples).

### Existing patterns to follow

- `SearchResource` / `ModelGraphRepository` for the JAX-RS + Cypher pattern
- Raw Neo4j driver (not OGM)
- Jackson serialization with `@JsonInclude(NON_NULL)`
- Deployment-scoped resources excluded (`NOT r.address STARTS WITH '/deployment'`)

### Test strategy

- **Unit tests:** Thorough coverage of query builders (filter → Cypher translation)
- **Integration tests:** Representative subset (~5-6 facets covering each type: one
  boolean, one multi-value, one sub-param, one relationship-based) plus pagination
  and sorting, run against real MGT Testcontainer

### Relationship reference

| Relationship | From | To | Used by facet |
|-------------|------|----|---------------|
| `HAS_ATTRIBUTE` | Resource | Attribute | (join) |
| `PROVIDES` | Resource | Operation | (join) |
| `DECLARES_CAPABILITY` | Resource | Capability | `declaresCap` |
| `REFERENCES_CAPABILITY` | Attribute | Capability | `refsCap` |
| `IS_SENSITIVE` | Attribute | Constraint | `sensitive` |
| `REQUIRES` | Attribute | Attribute | `hasRelationships` |
| `ALTERNATIVE` | Attribute | Attribute | `hasRelationships` |
| `CONSISTS_OF` | Attribute | Attribute | `dataType=complex` |
| `CHILD_OF` | Resource | Resource | (tree structure) |
