package org.wildfly.modelgraph.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelGraphRepositoryTest {

    @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
    @CsvSource({
            // single word — simple wildcard
            "pool, pool*",
            "default, default*",
            "suspend, suspend*",

            // hyphenated — split and AND
            "buffer-pool, buffer AND pool*",
            "http-listener, http AND listener*",
            "default-host, default AND host*",
            "max-pool-size, max AND pool AND size*",
            "read-resource, read AND resource*",
            "list-log-files, list AND log AND files*",

            // dotted — split and AND
            "org.wildfly, org AND wildfly*",
            "org.wildfly.batch, org AND wildfly AND batch*",

            // mixed dots and hyphens
            "org.wildfly.data-source, org AND wildfly AND data AND source*",
            "org.wildfly.io.buffer-pool, org AND wildfly AND io AND buffer AND pool*",

            // partial segments (user still typing)
            "default-h, default AND h*",
            "buffer-p, buffer AND p*",
            "org.wild, org AND wild*",
    })
    void escapeAndWildcard(String input, String expected) {
        assertEquals(expected, ModelGraphRepository.escapeAndWildcard(input));
    }

    @Test
    void escapeAndWildcardEscapesLuceneSpecialChars() {
        assertEquals("pool\\!test*", ModelGraphRepository.escapeAndWildcard("pool!test"));
        assertEquals("query\\(1\\)*", ModelGraphRepository.escapeAndWildcard("query(1)"));
    }

    @Test
    void escapeAndWildcardNeutralizesBooleanOperators() {
        assertEquals("pool AND or AND delete*", ModelGraphRepository.escapeAndWildcard("pool OR delete"));
        assertEquals("pool AND and AND something*", ModelGraphRepository.escapeAndWildcard("pool AND something"));
        assertEquals("not AND this*", ModelGraphRepository.escapeAndWildcard("NOT this"));
    }

    @Test
    void escapeAndWildcardSplitsOnSpaces() {
        assertEquals("buffer AND pool*", ModelGraphRepository.escapeAndWildcard("buffer pool"));
    }

    @Test
    void escapeAndWildcardHandlesLeadingDelimiters() {
        assertEquals("host*", ModelGraphRepository.escapeAndWildcard("-host"));
        assertEquals("SomeCapabilityName*", ModelGraphRepository.escapeAndWildcard(".SomeCapabilityName"));
        assertEquals("resource*", ModelGraphRepository.escapeAndWildcard("_resource"));
    }

    @Test
    void escapeAndWildcardHandlesTrailingDelimiter() {
        assertEquals("pool*", ModelGraphRepository.escapeAndWildcard("pool-"));
    }

    @Test
    void escapeAndWildcardHandlesDelimitersOnBothEnds() {
        assertEquals("middle AND part*", ModelGraphRepository.escapeAndWildcard("-middle-part-"));
    }

    @Test
    void escapeAndWildcardHandlesConsecutiveDelimiters() {
        assertEquals("buffer AND pool*", ModelGraphRepository.escapeAndWildcard("buffer--pool"));
        assertEquals("org AND wildfly*", ModelGraphRepository.escapeAndWildcard("org..wildfly"));
    }

    @Test
    void escapeAndWildcardHandlesMixedDelimiters() {
        assertEquals("org AND wildfly AND data AND source*",
                ModelGraphRepository.escapeAndWildcard("org.wildfly-data_source"));
    }

    @Test
    void escapeAndWildcardReturnsEmptyForDelimiterOnlyInput() {
        assertEquals("", ModelGraphRepository.escapeAndWildcard("---"));
        assertEquals("", ModelGraphRepository.escapeAndWildcard("..."));
        assertEquals("", ModelGraphRepository.escapeAndWildcard(" "));
    }
}
