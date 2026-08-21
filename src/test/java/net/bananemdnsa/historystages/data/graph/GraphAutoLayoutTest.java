package net.bananemdnsa.historystages.data.graph;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GraphAutoLayoutTest {

    /** Builds a prerequisite map: {@code deps("b", "a")} means b depends on a. */
    private static Map<String, Set<String>> graph(String... pairs) {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.computeIfAbsent(pairs[i], k -> new java.util.LinkedHashSet<>());
            out.computeIfAbsent(pairs[i + 1], k -> new java.util.LinkedHashSet<>());
            out.get(pairs[i]).add(pairs[i + 1]);
        }
        return out;
    }

    @Test
    void chainIsLaidOutLeftToRight() {
        // a -> b -> c
        Map<String, GraphPos> pos = GraphAutoLayout.compute(graph("b", "a", "c", "b"));

        assertEquals(0, pos.get("a").x());
        assertEquals(1, pos.get("b").x());
        assertEquals(2, pos.get("c").x());
    }

    @Test
    void longestPathWinsOverShortest() {
        // d depends on a and on c; c depends on b; b depends on a.
        // d must sit right of c, not next to b.
        Map<String, GraphPos> pos = GraphAutoLayout.compute(
                graph("b", "a", "c", "b", "d", "a", "d", "c"));

        assertEquals(0, pos.get("a").x());
        assertEquals(1, pos.get("b").x());
        assertEquals(2, pos.get("c").x());
        assertEquals(3, pos.get("d").x(), "d must land right of its deepest prerequisite");
    }

    @Test
    void independentComponentsDoNotOverlap() {
        Map<String, Set<String>> g = graph("b", "a", "y", "x");
        Map<String, GraphPos> pos = GraphAutoLayout.compute(g);

        int aRow = pos.get("a").y();
        int xRow = pos.get("x").y();
        assertNotEquals(aRow, xRow, "two components must be stacked, not drawn on top of each other");
    }

    @Test
    void islandsGoBelowEverythingElse() {
        Map<String, Set<String>> g = graph("b", "a");
        g.put("lonely", Set.of());

        Map<String, GraphPos> pos = GraphAutoLayout.compute(g);

        assertTrue(pos.get("lonely").y() > pos.get("a").y(),
                "an unconnected stage must not sit in the middle of the picture");
    }

    @Test
    void cycleDoesNotHang() {
        // a -> b -> a
        Map<String, GraphPos> pos = GraphAutoLayout.compute(graph("b", "a", "a", "b"));

        assertEquals(2, pos.size());
        assertNotNull(pos.get("a"));
        assertNotNull(pos.get("b"));
    }

    @Test
    void danglingPrerequisiteIsIgnored() {
        Map<String, Set<String>> g = new LinkedHashMap<>();
        g.put("a", Set.of("does_not_exist"));

        Map<String, GraphPos> pos = GraphAutoLayout.compute(g);

        assertEquals(1, pos.size());
        assertEquals(0, pos.get("a").x(), "a reference to a deleted stage must not shift the layer");
    }

    @Test
    void resultIsDeterministic() {
        Map<String, Set<String>> g = graph("b", "a", "c", "a", "d", "b", "d", "c");

        assertEquals(GraphAutoLayout.compute(g), GraphAutoLayout.compute(g),
                "the server hands these coordinates to every client — they must not vary");
    }

    @Test
    void emptyInputYieldsEmptyOutput() {
        assertTrue(GraphAutoLayout.compute(Map.of()).isEmpty());
    }
}
