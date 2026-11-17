package org.wildfly.modelgraph.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CypherQueryTest {

    // ------------------------------------------------------ basic construction

    @Test
    void emptyQueryProducesEmptyString() {
        assertEquals("", CypherQuery.create().toString());
    }

    @Test
    void matchProducesMatchClause() {
        String result = CypherQuery.create()
                .match("(r:Resource)")
                .toString();
        assertEquals("MATCH (r:Resource)", result);
    }

    // ------------------------------------------------------ full-text search

    @Test
    void fullTextSearchWithScore() {
        String result = CypherQuery.create()
                .fullTextSearch("mgt_search", "Resource")
                .toString();
        assertEquals(
                "CALL db.index.fulltext.queryNodes('mgt_search', $term) YIELD node, score\nWHERE node:Resource",
                result);
    }

    @Test
    void fullTextSearchWithoutScore() {
        String result = CypherQuery.create()
                .fullTextSearchNoScore("mgt_search", "Attribute")
                .toString();
        assertEquals(
                "CALL db.index.fulltext.queryNodes('mgt_search', $term) YIELD node\nWHERE node:Attribute",
                result);
    }

    // ------------------------------------------------------ where / and chaining

    @Test
    void firstWhereUsesWhere() {
        String result = CypherQuery.create()
                .match("(r:Resource)")
                .where("r.name = $name")
                .toString();
        assertEquals("MATCH (r:Resource) WHERE r.name = $name", result);
    }

    @Test
    void secondWhereUsesAnd() {
        String result = CypherQuery.create()
                .match("(r:Resource)")
                .where("r.name = $name")
                .where("r.stability = 'default'")
                .toString();
        assertEquals("MATCH (r:Resource) WHERE r.name = $name AND r.stability = 'default'", result);
    }

    @Test
    void fullTextSearchThenWhereUsesAnd() {
        String result = CypherQuery.create()
                .fullTextSearchNoScore("mgt_search", "Resource")
                .where("toLower(node.stability) IN $stabilities")
                .toString();
        assertEquals(
                "CALL db.index.fulltext.queryNodes('mgt_search', $term) YIELD node\n"
                        + "WHERE node:Resource AND toLower(node.stability) IN $stabilities",
                result);
    }

    // ------------------------------------------------------ whereIf

    @Test
    void whereIfTrueAddsCondition() {
        String result = CypherQuery.create()
                .match("(r:Resource)")
                .whereIf(true, "r.name = $name")
                .toString();
        assertEquals("MATCH (r:Resource) WHERE r.name = $name", result);
    }

    @Test
    void whereIfFalseSkipsCondition() {
        String result = CypherQuery.create()
                .match("(r:Resource)")
                .whereIf(false, "r.name = $name")
                .toString();
        assertEquals("MATCH (r:Resource)", result);
    }

    @Test
    void whereIfMixedConditions() {
        String result = CypherQuery.create()
                .match("(r:Resource)")
                .whereIf(true, "r.name = $name")
                .whereIf(false, "r.stability = 'default'")
                .whereIf(true, "r.singleton = true")
                .toString();
        assertEquals("MATCH (r:Resource) WHERE r.name = $name AND r.singleton = true", result);
    }

    // ------------------------------------------------------ with resets where tracking

    @Test
    void withResetsWhereTracking() {
        String result = CypherQuery.create()
                .match("(r:Resource)")
                .where("r.name = $name")
                .with("r")
                .where("r.stability = 'default'")
                .toString();
        assertEquals("MATCH (r:Resource) WHERE r.name = $name\nWITH r WHERE r.stability = 'default'", result);
    }

    // ------------------------------------------------------ returning

    @Test
    void returningAppendsReturnClause() {
        String result = CypherQuery.create()
                .match("(r:Resource)")
                .returning("r.name AS name")
                .toString();
        assertEquals("MATCH (r:Resource)\nRETURN r.name AS name", result);
    }

    // ------------------------------------------------------ order by

    @Test
    void orderByAppendsClause() {
        String result = CypherQuery.create()
                .match("(r:Resource)")
                .returning("r.name AS name")
                .orderBy("name ASC")
                .toString();
        assertEquals("MATCH (r:Resource)\nRETURN r.name AS name\nORDER BY name ASC", result);
    }

    // ------------------------------------------------------ append and newLine

    @Test
    void appendAddsRawText() {
        String result = CypherQuery.create()
                .match("(r:Resource)")
                .append(", score")
                .toString();
        assertEquals("MATCH (r:Resource), score", result);
    }

    @Test
    void newLineAddsLineBreak() {
        String result = CypherQuery.create()
                .match("(r:Resource)")
                .newLine()
                .match("(r)-[:HAS]->(a)")
                .toString();
        assertEquals("MATCH (r:Resource)\nMATCH (r)-[:HAS]->(a)", result);
    }

    // ------------------------------------------------------ complex query

    @Test
    void complexQueryCombinesAllClauses() {
        String result = CypherQuery.create()
                .fullTextSearchNoScore("mgt_search", "Attribute")
                .newLine()
                .match("(node)<-[:HAS_ATTRIBUTE]-(r:Resource)")
                .whereIf(true, "toLower(node.stability) IN $stabilities")
                .whereIf(true, "r.address STARTS WITH $scope")
                .returning("'attribute' AS type, node.name AS name")
                .toString();
        assertEquals(
                "CALL db.index.fulltext.queryNodes('mgt_search', $term) YIELD node\n"
                        + "WHERE node:Attribute\n"
                        + "MATCH (node)<-[:HAS_ATTRIBUTE]-(r:Resource)"
                        + " AND toLower(node.stability) IN $stabilities"
                        + " AND r.address STARTS WITH $scope\n"
                        + "RETURN 'attribute' AS type, node.name AS name",
                result);
    }
}
