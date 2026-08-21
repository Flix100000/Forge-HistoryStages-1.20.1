package net.bananemdnsa.historystages.data.graph;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GraphLayoutDataTest {

    @Test
    void parsesBothTrees() {
        String json = """
                {
                  "global":     { "steinzeit": [0, 0], "feuerzeit": [1, -2] },
                  "individual": { "erste_quest": [3, 4] }
                }
                """;

        GraphLayoutData.Snapshot snap = GraphLayoutData.fromJson(json);

        assertEquals(new GraphPos(0, 0), snap.global().get("steinzeit"));
        assertEquals(new GraphPos(1, -2), snap.global().get("feuerzeit"));
        assertEquals(new GraphPos(3, 4), snap.individual().get("erste_quest"));
    }

    @Test
    void sameIdInBothTreesStaysSeparate() {
        String json = """
                { "global": { "test": [1, 1] }, "individual": { "test": [9, 9] } }
                """;

        GraphLayoutData.Snapshot snap = GraphLayoutData.fromJson(json);

        assertEquals(new GraphPos(1, 1), snap.global().get("test"));
        assertEquals(new GraphPos(9, 9), snap.individual().get("test"),
                "global/test.json and individual/test.json are different stages");
    }

    @Test
    void roundTripsThroughJson() {
        Map<String, GraphPos> global = new LinkedHashMap<>();
        global.put("a", new GraphPos(0, 0));
        global.put("b", new GraphPos(1, -1));

        GraphLayoutData.Snapshot original = new GraphLayoutData.Snapshot(global, Map.of(), true, false);
        GraphLayoutData.Snapshot parsed = GraphLayoutData.fromJson(GraphLayoutData.toJson(original));

        assertEquals(original.global(), parsed.global());
        assertTrue(parsed.individual().isEmpty());
    }

    @Test
    void missingSectionsBecomeEmptyMaps() {
        GraphLayoutData.Snapshot snap = GraphLayoutData.fromJson("{}");

        assertTrue(snap.global().isEmpty());
        assertTrue(snap.individual().isEmpty());
    }

    @Test
    void malformedJsonYieldsEmptySnapshotInsteadOfThrowing() {
        GraphLayoutData.Snapshot snap = GraphLayoutData.fromJson("{ this is not json");

        assertTrue(snap.global().isEmpty());
        assertTrue(snap.individual().isEmpty());
    }

    @Test
    void malformedEntryIsSkippedButRestSurvives() {
        String json = """
                { "global": { "good": [1, 2], "bad": [1], "alsobad": "nope" } }
                """;

        GraphLayoutData.Snapshot snap = GraphLayoutData.fromJson(json);

        assertEquals(1, snap.global().size());
        assertEquals(new GraphPos(1, 2), snap.global().get("good"));
    }

    @Test
    void nonEmptySectionMeansFrozen() {
        GraphLayoutData.Snapshot snap = GraphLayoutData.fromJson("""
                { "global": { "a": [0, 0] }, "individual": {} }
                """);

        assertTrue(snap.isFrozen(false), "a tree with stored positions is author-owned");
        assertFalse(snap.isFrozen(true), "an empty tree still follows the algorithm");
    }

    @Test
    void computedPositionsDoNotCountAsFrozen() {
        // The regression this guards: frozen used to be derived from "the map is non-empty".
        // recomputeGraphLayout() fills an unfrozen tree's map with computed positions on every
        // load, so that rule reported every tree as author-owned before a player could act —
        // which silently skipped the "take over this layout?" confirmation and made the editor's
        // unplaced column appear out of nowhere.
        Map<String, GraphPos> computed = new LinkedHashMap<>();
        computed.put("steinzeit", new GraphPos(0, 0));

        GraphLayoutData.Snapshot unfrozen = GraphLayoutData.Snapshot.empty()
                .withPositions(false, computed);

        assertFalse(unfrozen.isFrozen(false), "computed positions are the algorithm's, not the author's");
        assertEquals(1, unfrozen.global().size(), "the positions themselves are still there");
    }

    @Test
    void withPositionsLeavesTheOtherTreeAndBothFlagsAlone() {
        GraphLayoutData.Snapshot start = new GraphLayoutData.Snapshot(
                Map.of("a", new GraphPos(1, 1)), Map.of("b", new GraphPos(2, 2)), true, false);

        GraphLayoutData.Snapshot updated = start.withPositions(true, Map.of("c", new GraphPos(3, 3)));

        assertTrue(updated.isFrozen(false));
        assertFalse(updated.isFrozen(true));
        assertEquals(start.global(), updated.global());
        assertEquals(new GraphPos(3, 3), updated.individual().get("c"));
    }
}
