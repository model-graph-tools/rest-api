package org.wildfly.modelgraph.api;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Lucene query escaping and tokenization utilities shared by search and catalog endpoints. */
public final class LuceneUtils {

    private static final Pattern BOOLEAN_OPERATORS = Pattern.compile("\\b(AND|OR|NOT)\\b");

    public static final String SEARCH_INDEX = "mgt_search";

    private LuceneUtils() {
    }

    /**
     * Splits the search term on hyphens, underscores, and spaces (but not dots), escapes Lucene special characters
     * in each part, joins them with {@code AND}, and appends a wildcard to the last part for prefix matching.
     */
    public static String escapeAndWildcard(String term) {
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

    /**
     * Splits the term on dots, hyphens, underscores, and spaces and lowercases each part. Capabilities use a
     * different tokenization than the full-text index because their dotted names (e.g., {@code org.wildfly.data-source})
     * are not indexed by the full-text search — they are matched via {@code ALL(part IN $capabilityTerms WHERE ...)}
     * in Cypher.
     */
    public static List<String> capabilityTerms(String term) {
        return Arrays.stream(term.split("[.\\s\\-_]+"))
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String escapeLucene(String s) {
        String escaped = s.replaceAll("([+&|!(){}\\[\\]^\"~*?:\\\\/])", "\\\\$1");
        return BOOLEAN_OPERATORS.matcher(escaped)
                .replaceAll(m -> m.group().toLowerCase());
    }
}
