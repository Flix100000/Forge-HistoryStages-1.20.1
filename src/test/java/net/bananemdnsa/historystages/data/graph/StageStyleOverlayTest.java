package net.bananemdnsa.historystages.data.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StageStyleOverlayTest {

    @Test
    void upperWinsWhereItIsSet() {
        StageStyle lower = new StageStyle();
        lower.shape = "HEXAGON";
        lower.size = 1.4;

        StageStyle upper = new StageStyle();
        upper.size = 2.0;

        StageStyle merged = StageStyle.overlay(lower, upper);

        assertEquals("HEXAGON", merged.shape, "a field only the lower layer sets must survive");
        assertEquals(2.0, merged.size);
    }

    @Test
    void nullLayersAreTolerated() {
        StageStyle only = new StageStyle();
        only.border = "#C04040";

        assertEquals("#C04040", StageStyle.overlay(null, only).border);
        assertEquals("#C04040", StageStyle.overlay(only, null).border);
        assertTrue(StageStyle.overlay(null, null).isEmpty());
    }

    @Test
    void overlayDoesNotMutateItsInputs() {
        StageStyle lower = new StageStyle();
        lower.size = 1.0;
        StageStyle upper = new StageStyle();
        upper.size = 2.0;

        StageStyle.overlay(lower, upper);

        assertEquals(1.0, lower.size, "overlay must return a new object, not edit the lower layer");
    }

    @Test
    void copyIsIndependent() {
        StageStyle original = new StageStyle();
        original.fill = "#112233";

        StageStyle copy = original.copy();
        copy.fill = "#445566";

        assertEquals("#112233", original.fill);
    }
}
