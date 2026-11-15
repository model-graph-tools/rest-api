package org.wildfly.modelgraph.api;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LuceneUtilsTest {

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

            // dotted — dots are NOT delimiters (standard analyzer keeps them as part of tokens)
            "org.wildfly, org.wildfly*",
            "org.wildfly.batch, org.wildfly.batch*",

            // mixed dots and hyphens — only split on hyphens
            "org.wildfly.data-source, org.wildfly.data AND source*",
            "org.wildfly.io.buffer-pool, org.wildfly.io.buffer AND pool*",

            // partial segments (user still typing)
            "default-h, default AND h*",
            "buffer-p, buffer AND p*",
            "org.wild, org.wild*",
    })
    void escapeAndWildcard(String input, String expected) {
        assertEquals(expected, LuceneUtils.escapeAndWildcard(input));
    }

    @Test
    void escapeAndWildcardEscapesLuceneSpecialChars() {
        assertEquals("pool\\!test*", LuceneUtils.escapeAndWildcard("pool!test"));
        assertEquals("query\\(1\\)*", LuceneUtils.escapeAndWildcard("query(1)"));
        assertEquals("path\\/to*", LuceneUtils.escapeAndWildcard("path/to"));
        assertEquals("a\\/b\\/c*", LuceneUtils.escapeAndWildcard("a/b/c"));
    }

    @Test
    void escapeAndWildcardNeutralizesBooleanOperators() {
        assertEquals("pool AND or AND delete*", LuceneUtils.escapeAndWildcard("pool OR delete"));
        assertEquals("pool AND and AND something*", LuceneUtils.escapeAndWildcard("pool AND something"));
        assertEquals("not AND this*", LuceneUtils.escapeAndWildcard("NOT this"));
    }

    @Test
    void escapeAndWildcardSplitsOnSpaces() {
        assertEquals("buffer AND pool*", LuceneUtils.escapeAndWildcard("buffer pool"));
    }

    @Test
    void escapeAndWildcardHandlesLeadingDelimiters() {
        assertEquals("host*", LuceneUtils.escapeAndWildcard("-host"));
        assertEquals("SomeCapabilityName*", LuceneUtils.escapeAndWildcard(".SomeCapabilityName"));
        assertEquals("resource*", LuceneUtils.escapeAndWildcard("_resource"));
    }

    @Test
    void escapeAndWildcardHandlesTrailingDelimiter() {
        assertEquals("pool*", LuceneUtils.escapeAndWildcard("pool-"));
    }

    @Test
    void escapeAndWildcardHandlesDelimitersOnBothEnds() {
        assertEquals("middle AND part*", LuceneUtils.escapeAndWildcard("-middle-part-"));
    }

    @Test
    void escapeAndWildcardHandlesConsecutiveDelimiters() {
        assertEquals("buffer AND pool*", LuceneUtils.escapeAndWildcard("buffer--pool"));
        assertEquals("org..wildfly*", LuceneUtils.escapeAndWildcard("org..wildfly"));
    }

    @Test
    void escapeAndWildcardHandlesMixedDelimiters() {
        assertEquals("org.wildfly AND data AND source*",
                LuceneUtils.escapeAndWildcard("org.wildfly-data_source"));
    }

    @Test
    void escapeAndWildcardReturnsEmptyForDelimiterOnlyInput() {
        assertEquals("", LuceneUtils.escapeAndWildcard("---"));
        assertEquals("", LuceneUtils.escapeAndWildcard("..."));
        assertEquals("", LuceneUtils.escapeAndWildcard(" "));
    }

    @Test
    void capabilityTermsSplitsOnAllDelimiters() {
        assertEquals(List.of("org", "wildfly", "data", "source"),
                LuceneUtils.capabilityTerms("org.wildfly.data-source"));
    }

    @Test
    void capabilityTermsSingleWord() {
        assertEquals(List.of("batch"), LuceneUtils.capabilityTerms("batch"));
    }

    @Test
    void capabilityTermsLowercases() {
        assertEquals(List.of("buffer", "pool"), LuceneUtils.capabilityTerms("Buffer-Pool"));
    }

    @Test
    void capabilityTermsSplitsOnSpaces() {
        assertEquals(List.of("buffer", "pool"), LuceneUtils.capabilityTerms("buffer pool"));
    }

    @Test
    void capabilityTermsSplitsOnDots() {
        assertEquals(List.of("org", "wildfly", "batch", "configuration"),
                LuceneUtils.capabilityTerms("org.wildfly.batch.configuration"));
    }

    @Test
    void capabilityTermsSplitsOnUnderscores() {
        assertEquals(List.of("some", "term"), LuceneUtils.capabilityTerms("some_term"));
    }

    @Test
    void capabilityTermsFiltersEmptyParts() {
        assertEquals(List.of("batch"), LuceneUtils.capabilityTerms("..batch.."));
    }
}
