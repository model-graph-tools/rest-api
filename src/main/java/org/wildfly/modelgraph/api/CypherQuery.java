package org.wildfly.modelgraph.api;

/** Fluent builder for Cypher query strings with automatic WHERE/AND clause chaining. */
public final class CypherQuery {

    private final StringBuilder sb;
    private boolean hasWhere;

    private CypherQuery() {
        this.sb = new StringBuilder();
        this.hasWhere = false;
    }

    public static CypherQuery create() {
        return new CypherQuery();
    }

    public CypherQuery fullTextSearch(String index, String label) {
        sb.append("CALL db.index.fulltext.queryNodes('").append(index).append("', $term) YIELD node, score\n");
        sb.append("WHERE node:").append(label);
        hasWhere = true;
        return this;
    }

    public CypherQuery fullTextSearchNoScore(String index, String label) {
        sb.append("CALL db.index.fulltext.queryNodes('").append(index).append("', $term) YIELD node\n");
        sb.append("WHERE node:").append(label);
        hasWhere = true;
        return this;
    }

    public CypherQuery match(String pattern) {
        sb.append("MATCH ").append(pattern);
        hasWhere = false;
        return this;
    }

    public CypherQuery where(String condition) {
        sb.append(hasWhere ? " AND " : " WHERE ");
        sb.append(condition);
        hasWhere = true;
        return this;
    }

    public CypherQuery whereIf(boolean predicate, String condition) {
        if (predicate) {
            where(condition);
        }
        return this;
    }

    public CypherQuery with(String clause) {
        sb.append("\nWITH ").append(clause);
        hasWhere = false;
        return this;
    }

    public CypherQuery returning(String clause) {
        sb.append("\nRETURN ").append(clause);
        return this;
    }

    public CypherQuery orderBy(String clause) {
        sb.append("\nORDER BY ").append(clause);
        return this;
    }

    public CypherQuery append(String text) {
        sb.append(text);
        return this;
    }

    public CypherQuery newLine() {
        sb.append("\n");
        return this;
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
