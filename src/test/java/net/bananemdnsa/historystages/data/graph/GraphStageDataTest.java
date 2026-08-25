package net.bananemdnsa.historystages.data.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraphStageDataTest {

    @Test
    void parsesDescriptionAndStyle() {
        String json = """
                {
                  "global": {
                    "endzeit": {
                      "description": "Der letzte Abschnitt.",
                      "style": { "shape": "hexagon", "size": 1.4, "border": "#c04040" }
                    }
                  },
                  "individual": {}
                }
                """;

        GraphStageData.Snapshot snap = GraphStageData.fromJson(json);

        assertEquals("Der letzte Abschnitt.", snap.description("endzeit", false));
        assertEquals("hexagon", snap.style("endzeit", false).shape);
        assertEquals(1.4, snap.style("endzeit", false).size);
        assertEquals("#c04040", snap.style("endzeit", false).border);
    }

    @Test
    void unknownStageYieldsNullDescriptionAndEmptyStyle() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson("{}");

        assertNull(snap.description("nope", false));
        assertTrue(snap.style("nope", false).isEmpty());
    }

    @Test
    void treesAreSeparateNamespaces() {
        String json = """
                {
                  "global":     { "test": { "description": "global one" } },
                  "individual": { "test": { "description": "individual one" } }
                }
                """;

        GraphStageData.Snapshot snap = GraphStageData.fromJson(json);

        assertEquals("global one", snap.description("test", false));
        assertEquals("individual one", snap.description("test", true));
    }

    @Test
    void descriptionSurvivesARoundTrip() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson("""
                { "global": { "a": { "description": "line one\\nline two" } } }
                """);

        GraphStageData.Snapshot again = GraphStageData.fromJson(GraphStageData.toJson(snap));

        assertEquals("line one\nline two", again.description("a", false));
    }

    @Test
    void styleSurvivesARoundTrip() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson("""
                { "global": { "a": { "style": { "shape": "diamond", "checkmark": false } } } }
                """);

        GraphStageData.Snapshot again = GraphStageData.fromJson(GraphStageData.toJson(snap));

        assertEquals("diamond", again.style("a", false).shape);
        assertEquals(Boolean.FALSE, again.style("a", false).checkmark);
    }

    @Test
    void malformedJsonYieldsEmptySnapshot() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson("{ broken");

        assertNull(snap.description("a", false));
    }

    @Test
    void settingADescriptionToBlankRemovesTheEntry() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson("""
                { "global": { "a": { "description": "text" } } }
                """);

        GraphStageData.Snapshot updated = snap.withDescription("a", false, "   ");

        assertNull(updated.description("a", false));
    }

    @Test
    void aFileWithoutStylesStillLoads() {
        String json = """
                { "global": { "endzeit": {
                    "description": "text",
                    "style": { "shape": "hexagon" } } }, "individual": {} }
                """;

        GraphStageData.Snapshot snapshot = GraphStageData.fromJson(json);

        assertEquals("text", snapshot.description("endzeit", false));
        assertEquals("hexagon", snapshot.style("endzeit", false).shape);
        assertNull(snapshot.style("endzeit", false, NodeState.LOCKED).border,
                "no styles block means no per-state override");
    }

    @Test
    void perStateOverrideLayersOnTopOfTheAllStatesOne() {
        String json = """
                { "global": { "endzeit": {
                    "style":  { "shape": "hexagon", "size": 1.4 },
                    "styles": { "locked": { "border": "#C04040", "size": 2.0 } } } },
                  "individual": {} }
                """;

        GraphStageData.Snapshot snapshot = GraphStageData.fromJson(json);

        StageStyle locked = snapshot.style("endzeit", false, NodeState.LOCKED);
        assertEquals("hexagon", locked.shape, "the all-states field must carry through");
        assertEquals("#C04040", locked.border);
        assertEquals(2.0, locked.size, "the per-state field must win");

        StageStyle unlocked = snapshot.style("endzeit", false, NodeState.UNLOCKED);
        assertNull(unlocked.border, "the locked override must not leak into another state");
        assertEquals(1.4, unlocked.size);
    }

    @Test
    void stylesSurviveARoundTrip() {
        String json = """
                { "global": { "endzeit": {
                    "styles": { "reachable": { "checkmark": true } } } },
                  "individual": {} }
                """;

        GraphStageData.Snapshot reparsed =
                GraphStageData.fromJson(GraphStageData.toJson(GraphStageData.fromJson(json)));

        assertEquals(Boolean.TRUE,
                reparsed.style("endzeit", false, NodeState.REACHABLE).checkmark);
    }

    @Test
    void withStyleKeepsTheDescription() {
        GraphStageData.Snapshot start = GraphStageData.fromJson(
                "{ \"global\": { \"a\": { \"description\": \"keep me\" } }, \"individual\": {} }");

        GraphStageData.Entry override = new GraphStageData.Entry();
        override.style = new StageStyle();
        override.style.shape = "CIRCLE";

        GraphStageData.Snapshot after = start.withStyle("a", false, override);

        assertEquals("keep me", after.description("a", false));
        assertEquals("CIRCLE", after.style("a", false).shape);
    }

    @Test
    void clearingTheLastFieldDropsTheEntry() {
        GraphStageData.Snapshot start = GraphStageData.fromJson(
                "{ \"global\": { \"a\": { \"style\": { \"shape\": \"CIRCLE\" } } }, \"individual\": {} }");

        GraphStageData.Snapshot after = start.withStyle("a", false, new GraphStageData.Entry());

        assertFalse(after.global().containsKey("a"),
                "an entry with neither description nor style must not stay in the file");
    }

    @Test
    void anEntryWithOnlyAPerStateOverrideIsNotEmpty() {
        GraphStageData.Entry entry = new GraphStageData.Entry();
        entry.styles = new StateStyles();
        entry.styles.locked = new StageStyle();
        entry.styles.locked.checkmark = Boolean.TRUE;

        assertFalse(entry.isEmpty());
    }
}
