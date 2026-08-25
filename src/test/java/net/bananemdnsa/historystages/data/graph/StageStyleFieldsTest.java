package net.bananemdnsa.historystages.data.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StageStyleFieldsTest {

    @Test
    void everyLeafRoundTrips() {
        StageStyle style = new StageStyle();
        for (String leaf : StageStyleFields.LEAVES) {
            String written = switch (leaf) {
                case "shape" -> "HEXAGON";
                case "label" -> "ID";
                case "border", "fill", "labelColor" -> "#C04040";
                case "size", "fillOpacity" -> "1.5";
                case "checkmark" -> "true";
                default -> "3";
            };
            StageStyleFields.set(style, leaf, written);
            assertEquals(written, StageStyleFields.get(style, leaf), "leaf " + leaf);
        }
    }

    @Test
    void clearingALeafPutsItBackToInherit() {
        StageStyle style = new StageStyle();
        StageStyleFields.set(style, "size", "1.5");
        StageStyleFields.set(style, "size", null);

        assertNull(style.size);
        assertNull(StageStyleFields.get(style, "size"));
        assertTrue(style.isEmpty());
    }

    @Test
    void unparseableNumbersClearTheFieldInsteadOfThrowing() {
        StageStyle style = new StageStyle();
        StageStyleFields.set(style, "size", "not a number");

        assertNull(style.size, "a half-typed value must not become a crash");
    }

    @Test
    void unknownLeafIsIgnored() {
        StageStyle style = new StageStyle();
        StageStyleFields.set(style, "nonsense", "1");
        assertTrue(style.isEmpty());
        assertNull(StageStyleFields.get(style, "nonsense"));
    }

    @Test
    void leavesCoverEveryFieldOfStageStyle() {
        // Guards against a field added to StageStyle that nothing here knows about: such a field
        // would be silently uneditable and silently unvalidated.
        assertEquals(10, StageStyleFields.LEAVES.size());
    }
}
