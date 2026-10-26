# Glossary

## Catalog

The faceted search page in halOP that exposes the full MGT data surface. The backend
is the `GET /api/catalog` endpoint.

## Facet

A dimension of the catalog result set used for aggregation counts. Each facet reports
how many results each value would produce if selected. Facet counts exclude the facet's
own filter but include all other active filters. Example: the `type` facet might show
`{ "resource": 42, "attribute": 128 }`.

## Filter

A query parameter applied to narrow the catalog result set. Filters use OR semantics
within a single parameter and AND semantics across parameters.

## Management Model Version

The versioning scheme used by WildFly's management API, stored as `Version` nodes in
the graph with `major`, `minor`, `patch` integer properties. These are independent from
WildFly release versions — there is no mapping between them. Example: management model
version `24.0.0` exists across WildFly 35, 41, and 42.

## Scope

An address prefix used to filter all catalog types to a resource subtree. Applied as
`STARTS WITH` on resource addresses (or parent resource addresses for attributes and
operations).

## Type

One of four node kinds in the catalog: `resource`, `attribute`, `operation`, `capability`.
Always lowercase in the API (both query parameters and responses).
