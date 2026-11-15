package org.wildfly.modelgraph.api.operation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Value;

/** Queries the Neo4j graph for detailed operation information including parameters and return value. */
@ApplicationScoped
public class OperationRepository {

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

    @Inject
    Driver driver;

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
}
