package org.wildfly.modelgraph.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.ClientException;

@ApplicationScoped
public class ModelGraphRepository {

    static final String SEARCH_INDEX = "mgt_search";

    private static final String SEARCH_QUERY = """
            CALL {
                CALL db.index.fulltext.queryNodes('%s', $term) YIELD node, score
                WHERE node:Resource
                  AND NOT node.address STARTS WITH '/deployment'
                RETURN 'resource' AS type, node.name AS name, node.description AS description,
                       node.address AS address, null AS providedBy, node.stability AS stability,
                       node.singleton AS singleton, score
                ORDER BY score DESC, node.name
                LIMIT $limit
            }
            RETURN type, name, description, address, providedBy, stability, singleton, score
            UNION ALL
            CALL {
                CALL db.index.fulltext.queryNodes('%s', $term) YIELD node, score
                WHERE node:Attribute
                MATCH (node)<-[:HAS_ATTRIBUTE]-(r:Resource)
                WHERE NOT r.address STARTS WITH '/deployment'
                RETURN 'attribute' AS type, node.name AS name, node.description AS description,
                       r.address AS address, null AS providedBy, node.stability AS stability,
                       r.singleton AS singleton, score
                ORDER BY score DESC, node.name
                LIMIT $limit
            }
            RETURN type, name, description, address, providedBy, stability, singleton, score
            UNION ALL
            CALL {
                MATCH (node:Capability)
                WHERE ALL(part IN $capabilityTerms WHERE
                    toLower(replace(node.name, '-', '')) CONTAINS part)
                OPTIONAL MATCH (node)<-[:DECLARES_CAPABILITY]-(r:Resource)
                WHERE NOT r.address STARTS WITH '/deployment'
                WITH node,
                     COLLECT(CASE WHEN r IS NOT NULL THEN {name: r.name, address: r.address} END) AS providedBy
                RETURN 'capability' AS type, node.name AS name, null AS description,
                       null AS address, providedBy, node.stability AS stability,
                       null AS singleton, 1.0 AS score
                ORDER BY node.name
                LIMIT $limit
            }
            RETURN type, name, description, address, providedBy, stability, singleton, score
            UNION ALL
            CALL {
                CALL db.index.fulltext.queryNodes('%s', $term) YIELD node, score
                WHERE node:Operation AND NOT node.global
                MATCH (node)<-[:PROVIDES]-(r:Resource)
                WHERE NOT r.address STARTS WITH '/deployment'
                RETURN 'operation' AS type, node.name AS name, node.description AS description,
                       r.address AS address, null AS providedBy, node.stability AS stability,
                       r.singleton AS singleton, score
                ORDER BY score DESC, node.name
                LIMIT $limit
            }
            RETURN type, name, description, address, providedBy, stability, singleton, score
            """.formatted(SEARCH_INDEX, SEARCH_INDEX, SEARCH_INDEX);

    private static final String CAPABILITY_REFERENCES_QUERY = """
            MATCH (c:Capability {name: $name})<-[:REFERENCES_CAPABILITY]-(a:Attribute)<-[:HAS_ATTRIBUTE]-(r:Resource)
            WHERE NOT r.address STARTS WITH '/deployment'
            RETURN a.name AS attributeName, a.stability AS attributeStability,
                   r.name AS resourceName, r.address AS resourceAddress, r.stability AS resourceStability
            ORDER BY r.address
            """;

    private static final String RESOURCE_DETAIL_QUERY = """
            MATCH (r:Resource {address: $address})
            OPTIONAL MATCH (r)-[:CHILD_OF]->(parent:Resource)
            OPTIONAL MATCH (r)-[rdep:DEPRECATED_SINCE]->(dv:Version)
            WITH r, parent.address AS parentAddress,
                 CASE WHEN dv IS NOT NULL THEN {since: dv.major + '.' + dv.minor + '.' + dv.ordinal, reason: rdep.reason} END AS deprecation
            OPTIONAL MATCH (child:Resource)-[:CHILD_OF]->(r)
            WITH r, parentAddress, deprecation, child
            ORDER BY child.name
            WITH r, parentAddress, deprecation,
                 COLLECT(CASE WHEN child IS NOT NULL THEN {name: child.name, address: child.address} END) AS children
            OPTIONAL MATCH (r)-[:HAS_ATTRIBUTE]->(a:Attribute)
            OPTIONAL MATCH (a)-[adep:DEPRECATED_SINCE]->()
            WITH r, parentAddress, deprecation, children, a, adep
            ORDER BY a.name
            WITH r, parentAddress, deprecation, children,
                 COLLECT(CASE WHEN a IS NOT NULL THEN {
                     name: a.name, type: a.type, stability: a.stability,
                     accessType: a.`access-type`, storage: a.storage,
                     deprecated: adep IS NOT NULL
                 } END) AS attributes
            OPTIONAL MATCH (r)-[:PROVIDES]->(o:Operation)
            OPTIONAL MATCH (o)-[odep:DEPRECATED_SINCE]->()
            WITH r, parentAddress, deprecation, children, attributes, o, odep
            ORDER BY o.name
            WITH r, parentAddress, deprecation, children, attributes,
                 COLLECT(CASE WHEN o IS NOT NULL THEN {
                     name: o.name, stability: o.stability,
                     readOnly: o.`read-only`, runtimeOnly: o.`runtime-only`, global: o.global,
                     deprecated: odep IS NOT NULL
                 } END) AS operations
            OPTIONAL MATCH (r)-[:DECLARES_CAPABILITY]->(c:Capability)
            WITH r, parentAddress, deprecation, children, attributes, operations, c
            ORDER BY c.name
            WITH r, parentAddress, deprecation, children, attributes, operations,
                 COLLECT(CASE WHEN c IS NOT NULL THEN {name: c.name} END) AS capabilities
            RETURN r.name AS name, r.address AS address, r.description AS description,
                   r.stability AS stability, r.singleton AS singleton,
                   parentAddress, deprecation, children, attributes, operations, capabilities
            """;

    private static final String ATTRIBUTE_DETAIL_QUERY = """
            MATCH (r:Resource {address: $resource})-[:HAS_ATTRIBUTE]->(a:Attribute {name: $name})
            OPTIONAL MATCH (a)-[adep:DEPRECATED_SINCE]->(dv:Version)
            WITH r, a,
                 CASE WHEN dv IS NOT NULL THEN {since: dv.major + '.' + dv.minor + '.' + dv.ordinal, reason: adep.reason} END AS deprecation
            OPTIONAL MATCH (a)-[:IS_SENSITIVE]->()
            WITH r, a, deprecation, COUNT(*) > 0 AND EXISTS { (a)-[:IS_SENSITIVE]->() } AS sensitive
            OPTIONAL MATCH (a)-[:REFERENCES_CAPABILITY]->(cap:Capability)
            WITH r, a, deprecation, sensitive, cap.name AS referencesCapability
            OPTIONAL MATCH (a)-[:REQUIRES]->(req:Attribute)
            WITH r, a, deprecation, sensitive, referencesCapability, req
            ORDER BY req.name
            WITH r, a, deprecation, sensitive, referencesCapability,
                 COLLECT(CASE WHEN req IS NOT NULL THEN {name: req.name} END) AS requires
            OPTIONAL MATCH (a)-[:ALTERNATIVE]->(alt:Attribute)
            WITH r, a, deprecation, sensitive, referencesCapability, requires, alt
            ORDER BY alt.name
            WITH r, a, deprecation, sensitive, referencesCapability, requires,
                 COLLECT(CASE WHEN alt IS NOT NULL THEN {name: alt.name} END) AS alternatives
            OPTIONAL MATCH (a)-[:CONSISTS_OF]->(sub:Attribute)
            WITH r, a, deprecation, sensitive, referencesCapability, requires, alternatives, sub
            ORDER BY sub.name
            WITH r, a, deprecation, sensitive, referencesCapability, requires, alternatives,
                 COLLECT(CASE WHEN sub IS NOT NULL THEN {name: sub.name, type: sub.type} END) AS subAttributes
            RETURN a.name AS name, a.description AS description, a.type AS type, a.stability AS stability,
                   a.required AS required, a.nillable AS nillable, a.`expressions-allowed` AS expressionsAllowed,
                   a.`access-type` AS accessType, a.storage AS storage, a.`restart-required` AS restartRequired,
                   a.`attribute-group` AS attributeGroup, a.unit AS unit, a.`default` AS defaultValue,
                   a.allowed AS allowedValues,
                   deprecation, sensitive,
                   r.name AS resourceName, r.address AS resourceAddress,
                   referencesCapability, requires, alternatives, subAttributes
            """;

    private static final String OPERATION_DETAIL_QUERY = """
            MATCH (r:Resource {address: $resource})-[:PROVIDES]->(o:Operation {name: $name})
            OPTIONAL MATCH (o)-[odep:DEPRECATED_SINCE]->(dv:Version)
            WITH r, o,
                 CASE WHEN dv IS NOT NULL THEN {since: dv.major + '.' + dv.minor + '.' + dv.ordinal, reason: odep.reason} END AS deprecation
            OPTIONAL MATCH (o)-[:ACCEPTS]->(p:Parameter)
            WITH r, o, deprecation, p
            ORDER BY p.name
            WITH r, o, deprecation,
                 COLLECT(CASE WHEN p IS NOT NULL THEN {
                     name: p.name, type: p.type, description: p.description,
                     required: p.required, nillable: p.nillable,
                     defaultValue: p.`default`, allowedValues: p.allowed
                 } END) AS parameters
            RETURN o.name AS name, o.description AS description, o.stability AS stability,
                   o.`read-only` AS readOnly, o.`runtime-only` AS runtimeOnly, o.global AS global,
                   deprecation,
                   r.name AS resourceName, r.address AS resourceAddress,
                   parameters,
                   o.`return-type` AS returnType, o.`return-description` AS returnDescription
            """;

    private static final String CAPABILITY_DETAIL_QUERY = """
            MATCH (c:Capability {name: $name})
            OPTIONAL MATCH (c)<-[:DECLARES_CAPABILITY]-(r:Resource)
            WITH c, r
            ORDER BY r.name
            WITH c, COLLECT(CASE WHEN r IS NOT NULL THEN {resourceName: r.name, resourceAddress: r.address} END) AS declaredBy
            OPTIONAL MATCH (c)<-[:REFERENCES_CAPABILITY]-(a:Attribute)<-[:HAS_ATTRIBUTE]-(res:Resource)
            WITH c, declaredBy, a, res
            ORDER BY a.name
            WITH c, declaredBy,
                 COLLECT(CASE WHEN a IS NOT NULL THEN {
                     attributeName: a.name, attributeStability: a.stability,
                     resourceName: res.name, resourceAddress: res.address, resourceStability: res.stability
                 } END) AS referencedBy
            RETURN c.name AS name, c.stability AS stability, declaredBy, referencedBy
            """;

    private static final String IDENTITY_QUERY = """
            MATCH (i:Identity)
            RETURN i.identifier AS identifier, i.`group-id` AS groupId, i.`artifact-id` AS artifactId,
                   i.name AS name, i.version AS version, i.type AS type,
                   i.description AS description, i.url AS url, i.`scm-url` AS scmUrl,
                   i.licenses AS licenses
            LIMIT 1
            """;

    private static final Logger LOG = Logger.getLogger(ModelGraphRepository.class);

    @Inject
    Driver driver;

    public List<SearchResult> search(String term, int limit) {
        String luceneTerm = escapeAndWildcard(term);
        if (luceneTerm.isEmpty()) {
            return List.of();
        }
        List<String> capTerms = capabilityTerms(term);
        try (var session = driver.session()) {
            return session.run(SEARCH_QUERY,
                            Map.of("term", luceneTerm, "limit", limit, "capabilityTerms", capTerms))
                    .list(ModelGraphRepository::toSearchResult);
        } catch (ClientException e) {
            LOG.warnf("Invalid search query '%s' (escaped: '%s'): %s", term, luceneTerm, e.getMessage());
            return List.of();
        }
    }

    static String escapeAndWildcard(String term) {
        // Strip leading/trailing delimiters (including dots — a leading dot is noise).
        // But do NOT split on dots — Neo4j's standard analyzer keeps them as part of tokens
        // (e.g., "org.wildfly.io.buffer" is one token). Only split on hyphens, underscores, and spaces.
        String stripped = term.replaceAll("^[\\s\\-._]+|[\\s\\-._]+$", "");
        if (stripped.isEmpty()) {
            return "";
        }
        String[] parts = stripped.split("[\\s\\-_]+");
        if (parts.length <= 1) {
            String escaped = escapeLucene(stripped);
            return escaped + "*";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(" AND ");
            }
            String escaped = escapeLucene(parts[i]);
            if (i == parts.length - 1) {
                sb.append(escaped).append("*");
            } else {
                sb.append(escaped);
            }
        }
        return sb.toString();
    }

    static List<String> capabilityTerms(String term) {
        return Arrays.stream(term.split("[.\\s\\-_]+"))
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String escapeLucene(String s) {
        String escaped = s.replaceAll("([+&|!(){}\\[\\]^\"~*?:\\\\/])", "\\\\$1");
        return java.util.regex.Pattern.compile("\\b(AND|OR|NOT)\\b")
                .matcher(escaped)
                .replaceAll(m -> m.group().toLowerCase());
    }

    public List<CapabilityReference> capabilityReferences(String name) {
        try (var session = driver.session()) {
            return session.run(CAPABILITY_REFERENCES_QUERY, Map.of("name", name))
                    .list(ModelGraphRepository::toCapabilityReference);
        }
    }

    public Optional<AttributeDetailResponse> attributeDetail(String resource, String name) {
        try (var session = driver.session()) {
            var result = session.run(ATTRIBUTE_DETAIL_QUERY, Map.of("resource", resource, "name", name));
            if (result.hasNext()) {
                var record = result.next();
                AttributeDetailResponse.Deprecation deprecation = null;
                Value depValue = record.get("deprecation");
                if (!depValue.isNull()) {
                    deprecation = new AttributeDetailResponse.Deprecation(
                            depValue.get("since").asString(null),
                            depValue.get("reason").asString(null));
                }
                Value allowedValue = record.get("allowedValues");
                List<String> allowedValues = allowedValue.isNull() ? null
                        : allowedValue.asList(Value::asString);
                List<AttributeDetailResponse.AttributeRef> requires = record.get("requires")
                        .asList(v -> new AttributeDetailResponse.AttributeRef(v.get("name").asString(null)));
                List<AttributeDetailResponse.AttributeRef> alternatives = record.get("alternatives")
                        .asList(v -> new AttributeDetailResponse.AttributeRef(v.get("name").asString(null)));
                List<AttributeDetailResponse.SubAttribute> subAttributes = record.get("subAttributes")
                        .asList(v -> new AttributeDetailResponse.SubAttribute(
                                v.get("name").asString(null),
                                v.get("type").asString(null)));
                return Optional.of(new AttributeDetailResponse(
                        record.get("name").asString(null),
                        record.get("description").asString(null),
                        record.get("type").asString(null),
                        record.get("stability").asString(null),
                        record.get("required").asBoolean(false),
                        record.get("nillable").asBoolean(false),
                        record.get("expressionsAllowed").asBoolean(false),
                        record.get("accessType").asString(null),
                        record.get("storage").asString(null),
                        record.get("restartRequired").asString(null),
                        record.get("attributeGroup").asString(null),
                        record.get("unit").asString(null),
                        record.get("defaultValue").asString(null),
                        allowedValues,
                        deprecation,
                        record.get("sensitive").asBoolean(false),
                        record.get("resourceName").asString(null),
                        record.get("resourceAddress").asString(null),
                        record.get("referencesCapability").asString(null),
                        requires, alternatives, subAttributes));
            }
            return Optional.empty();
        }
    }

    public Optional<OperationDetailResponse> operationDetail(String resource, String name) {
        try (var session = driver.session()) {
            var result = session.run(OPERATION_DETAIL_QUERY, Map.of("resource", resource, "name", name));
            if (result.hasNext()) {
                var record = result.next();
                OperationDetailResponse.Deprecation deprecation = null;
                Value depValue = record.get("deprecation");
                if (!depValue.isNull()) {
                    deprecation = new OperationDetailResponse.Deprecation(
                            depValue.get("since").asString(null),
                            depValue.get("reason").asString(null));
                }
                List<OperationDetailResponse.Parameter> parameters = record.get("parameters")
                        .asList(v -> {
                            Value allowedValue = v.get("allowedValues");
                            List<String> allowedValues = allowedValue.isNull() ? null
                                    : allowedValue.asList(Value::asString);
                            return new OperationDetailResponse.Parameter(
                                    v.get("name").asString(null),
                                    v.get("type").asString(null),
                                    v.get("description").asString(null),
                                    v.get("required").asBoolean(false),
                                    v.get("nillable").asBoolean(false),
                                    v.get("defaultValue").asString(null),
                                    allowedValues);
                        });
                OperationDetailResponse.ReturnValue returnValue = null;
                String returnType = record.get("returnType").asString(null);
                if (returnType != null) {
                    returnValue = new OperationDetailResponse.ReturnValue(
                            returnType,
                            record.get("returnDescription").asString(null));
                }
                return Optional.of(new OperationDetailResponse(
                        record.get("name").asString(null),
                        record.get("description").asString(null),
                        record.get("stability").asString(null),
                        record.get("readOnly").asBoolean(false),
                        record.get("runtimeOnly").asBoolean(false),
                        record.get("global").asBoolean(false),
                        deprecation,
                        record.get("resourceName").asString(null),
                        record.get("resourceAddress").asString(null),
                        parameters, returnValue));
            }
            return Optional.empty();
        }
    }

    public Optional<ResourceDetailResponse> resourceDetail(String address) {
        try (var session = driver.session()) {
            var result = session.run(RESOURCE_DETAIL_QUERY, Map.of("address", address));
            if (result.hasNext()) {
                var record = result.next();
                ResourceDetailResponse.Deprecation deprecation = null;
                Value depValue = record.get("deprecation");
                if (!depValue.isNull()) {
                    deprecation = new ResourceDetailResponse.Deprecation(
                            depValue.get("since").asString(null),
                            depValue.get("reason").asString(null));
                }
                List<ResourceDetailResponse.ChildResource> children = record.get("children")
                        .asList(v -> new ResourceDetailResponse.ChildResource(
                                v.get("name").asString(null),
                                v.get("address").asString(null)));
                List<ResourceDetailResponse.AttributeSummary> attributes = record.get("attributes")
                        .asList(v -> new ResourceDetailResponse.AttributeSummary(
                                v.get("name").asString(null),
                                v.get("type").asString(null),
                                v.get("stability").asString(null),
                                v.get("accessType").asString(null),
                                v.get("storage").asString(null),
                                v.get("deprecated").asBoolean(false)));
                List<ResourceDetailResponse.OperationSummary> operations = record.get("operations")
                        .asList(v -> new ResourceDetailResponse.OperationSummary(
                                v.get("name").asString(null),
                                v.get("stability").asString(null),
                                v.get("readOnly").asBoolean(false),
                                v.get("runtimeOnly").asBoolean(false),
                                v.get("global").asBoolean(false),
                                v.get("deprecated").asBoolean(false)));
                List<ResourceDetailResponse.CapabilitySummary> capabilities = record.get("capabilities")
                        .asList(v -> new ResourceDetailResponse.CapabilitySummary(
                                v.get("name").asString(null)));
                return Optional.of(new ResourceDetailResponse(
                        record.get("name").asString(null),
                        record.get("address").asString(null),
                        record.get("description").asString(null),
                        record.get("stability").asString(null),
                        record.get("singleton").asBoolean(false),
                        deprecation,
                        record.get("parentAddress").asString(null),
                        children, attributes, operations, capabilities));
            }
            return Optional.empty();
        }
    }

    public Optional<CapabilityDetailResponse> capabilityDetail(String name) {
        try (var session = driver.session()) {
            var result = session.run(CAPABILITY_DETAIL_QUERY, Map.of("name", name));
            if (result.hasNext()) {
                var record = result.next();
                List<CapabilityDetailResponse.DeclaredBy> declaredBy = record.get("declaredBy")
                        .asList(v -> new CapabilityDetailResponse.DeclaredBy(
                                v.get("resourceName").asString(null),
                                v.get("resourceAddress").asString(null)));
                List<CapabilityReference> referencedBy = record.get("referencedBy")
                        .asList(v -> new CapabilityReference(
                                v.get("attributeName").asString(null),
                                v.get("attributeStability").asString(null),
                                v.get("resourceName").asString(null),
                                v.get("resourceAddress").asString(null),
                                v.get("resourceStability").asString(null)));
                return Optional.of(new CapabilityDetailResponse(
                        record.get("name").asString(null),
                        record.get("stability").asString(null),
                        declaredBy,
                        referencedBy));
            }
            return Optional.empty();
        }
    }

    public IdentityResponse identity() {
        try (var session = driver.session()) {
            var result = session.run(IDENTITY_QUERY);
            if (result.hasNext()) {
                var record = result.next();
                return new IdentityResponse(
                        record.get("identifier").asString(null),
                        record.get("groupId").asString(null),
                        record.get("artifactId").asString(null),
                        record.get("name").asString(null),
                        record.get("version").asString(null),
                        record.get("type").asString(null),
                        record.get("description").asString(null),
                        record.get("url").asString(null),
                        record.get("scmUrl").asString(null),
                        record.get("licenses").asString(null));
            }
            return new IdentityResponse(null, null, null, null, null, null, null, null, null, null);
        }
    }

    private static SearchResult toSearchResult(Record record) {
        Value providedByValue = record.get("providedBy");
        List<ResourceRef> providedBy = null;
        if (!providedByValue.isNull() && !providedByValue.isEmpty()) {
            providedBy = providedByValue.asList(v ->
                    new ResourceRef(v.get("name").asString(null), v.get("address").asString(null)));
        }
        Value singletonValue = record.get("singleton");
        Boolean singleton = singletonValue.isNull() ? null : singletonValue.asBoolean();
        return new SearchResult(
                record.get("type").asString(),
                record.get("name").asString(null),
                providedBy,
                record.get("description").asString(null),
                record.get("address").asString(null),
                record.get("stability").asString(null),
                singleton);
    }

    private static CapabilityReference toCapabilityReference(Record record) {
        return new CapabilityReference(
                record.get("attributeName").asString(null),
                record.get("attributeStability").asString(null),
                record.get("resourceName").asString(null),
                record.get("resourceAddress").asString(null),
                record.get("resourceStability").asString(null));
    }

}
