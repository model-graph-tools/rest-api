package org.wildfly.modelgraph.api;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Facet counts for the catalog response. Common facets (type, stability, deprecated) are always present. Type-specific facets are included only when results of that type are possible given the active filters.")
public record CatalogFacets(

        // ------------------------------------------------------ common (always present)

        @Schema(description = "Count per node type (resource, attribute, operation, capability). Always present.", required = true)
        Map<String, Long> type,

        @Schema(description = "Count per stability level. Always present.", required = true)
        Map<String, Long> stability,

        @Schema(description = "Count of deprecated (true) vs non-deprecated (false) elements. Always present.", required = true)
        Map<String, Long> deprecated,

        // ------------------------------------------------------ resource-specific

        @Schema(description = "Count of singleton vs non-singleton resources. Present when results include resources.")
        Map<String, Long> singleton,

        @Schema(description = "Count of resources that declare capabilities (yes/no). Present when results include resources.")
        Map<String, Long> declaresCap,

        @Schema(description = "Count of resources that define attribute groups. Present when results include resources.")
        Map<String, Long> definesAttrGroups,

        // ------------------------------------------------------ attribute-specific

        @Schema(description = "Count per attribute data type. Present when results include attributes.")
        Map<String, Long> dataType,

        @Schema(description = "Count per attribute access type (read-write, read-only, metric). Present when results include attributes.")
        Map<String, Long> accessType,

        @Schema(description = "Count per attribute storage type (configuration, runtime). Present when results include attributes.")
        Map<String, Long> storage,

        @Schema(description = "Count per attribute restart policy. Present when results include attributes.")
        Map<String, Long> restart,

        @Schema(description = "Count of required vs non-required attributes. Present when results include attributes.")
        Map<String, Long> required,

        @Schema(description = "Count of nillable vs non-nillable attributes. Present when results include attributes.")
        Map<String, Long> nillable,

        @Schema(description = "Count of attributes that allow expressions vs those that don't. Present when results include attributes.")
        Map<String, Long> expressions,

        @Schema(description = "Count per measurement unit. Present when results include attributes.")
        Map<String, Long> unit,

        @Schema(description = "Count of attributes with allowed values vs those without. Present when results include attributes.")
        Map<String, Long> hasAllowed,

        @Schema(description = "Count of sensitive vs non-sensitive attributes. Present when results include attributes.")
        Map<String, Long> sensitive,

        @Schema(description = "Count of attributes with REQUIRES dependencies. Present when results include attributes.")
        Map<String, Long> hasRequires,

        @Schema(description = "Count of attributes with ALTERNATIVE dependencies. Present when results include attributes.")
        Map<String, Long> hasAlternatives,

        @Schema(description = "Count of attributes that reference capabilities. Present when results include attributes.")
        Map<String, Long> refsCap,

        // ------------------------------------------------------ operation-specific

        @Schema(description = "Count of read-only vs read-write operations. Present when results include operations.")
        Map<String, Long> readOnly,

        @Schema(description = "Count of runtime-only vs non-runtime-only operations. Present when results include operations.")
        Map<String, Long> runtimeOnly,

        @Schema(description = "Count of global vs non-global operations. Present when results include operations.")
        Map<String, Long> global,

        @Schema(description = "Count of operations with parameters vs those without. Present when results include operations.")
        Map<String, Long> hasParams,

        @Schema(description = "Count of operations with a return value vs those without. Present when results include operations.")
        Map<String, Long> hasReturnValue) {

    static CatalogFacets commonOnly(Map<String, Long> type, Map<String, Long> stability,
            Map<String, Long> deprecated) {
        return new CatalogFacets(type, stability, deprecated,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
