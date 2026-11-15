package org.wildfly.modelgraph.api.attribute;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Value;

/** Queries the Neo4j graph for detailed attribute information including relationships, deprecation, and sub-attributes. */
@ApplicationScoped
public class AttributeRepository {

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
            With r, a, deprecation, sensitive, referencesCapability, requires, alt
            ORDER BY alt.name
            WITH r, a, deprecation, sensitive, referencesCapability, requires,
                 COLLECT(CASE WHEN alt IS NOT NULL THEN {name: alt.name} END) AS alternatives
            OPTIONAL MATCH (a)-[:CONSISTS_OF]->(sub:Attribute)
            WITH r, a, deprecation, sensitive, referencesCapability, requires, alternatives, sub
            ORDER BY sub.name
            With r, a, deprecation, sensitive, referencesCapability, requires, alternatives,
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

    @Inject
    Driver driver;

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
}
