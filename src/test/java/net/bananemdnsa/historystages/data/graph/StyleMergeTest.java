package net.bananemdnsa.historystages.data.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StyleMergeTest {

    private static ResolvedStyle base() {
        return new ResolvedStyle("ROUNDED", 1.0, 4, 0x44CC99, 2, 0x2E8B62, 0.35,
                "DISPLAY_NAME", 0xDDDDDD, true);
    }

    @Test
    void emptyOverrideChangesNothing() {
        assertEquals(base(), ResolvedStyle.merge(base(), new StageStyle()));
    }

    @Test
    void nullOverrideChangesNothing() {
        assertEquals(base(), ResolvedStyle.merge(base(), null));
    }

    @Test
    void onlySuppliedFieldsAreOverridden() {
        StageStyle override = new StageStyle();
        override.border = "#C04040";

        ResolvedStyle merged = ResolvedStyle.merge(base(), override);

        assertEquals(0xC04040, merged.border());
        assertEquals(base().fill(), merged.fill(), "an untouched field must survive");
        assertEquals(base().size(), merged.size());
    }

    @Test
    void shapeIsCaseInsensitive() {
        StageStyle override = new StageStyle();
        override.shape = "hexagon";

        assertEquals("HEXAGON", ResolvedStyle.merge(base(), override).shape());
    }

    @Test
    void colorParsesWithAndWithoutHash() {
        assertEquals(0xC04040, ResolvedStyle.parseColor("#c04040", 0));
        assertEquals(0xC04040, ResolvedStyle.parseColor("C04040", 0));
    }

    @Test
    void garbageColorFallsBackInsteadOfThrowing() {
        assertEquals(0x123456, ResolvedStyle.parseColor("not a colour", 0x123456));
        assertEquals(0x123456, ResolvedStyle.parseColor(null, 0x123456));
    }

    @Test
    void falseIsAnOverrideNotAnAbsence() {
        StageStyle override = new StageStyle();
        override.checkmark = false;

        assertFalse(ResolvedStyle.merge(base(), override).checkmark(),
                "Boolean.FALSE must override a true base — this is why the field is boxed");
    }

    private static ResolvedStyle withOpacity(double opacity) {
        return new ResolvedStyle("ROUNDED", 1.0, 4, 0x44CC99, 2, 0x2E8B62, opacity,
                "DISPLAY_NAME", 0xDDDDDD, true);
    }

    @Test
    void fillArgbAppliesOpacity() {
        assertEquals(0xFF2E8B62, withOpacity(1.0).fillArgb());
        assertEquals(0x802E8B62, withOpacity(0.5019608).fillArgb());
    }

    @Test
    void fillArgbNeverProducesZeroAlpha() {
        // The blit path reads a zero alpha byte as "no alpha given" and draws the node opaque,
        // so a fully transparent fill would come out solid — the exact opposite of the setting.
        assertEquals(0x012E8B62, withOpacity(0.0).fillArgb());
    }

    @Test
    void fillArgbClampsOutOfRangeOpacity() {
        assertEquals(0xFF2E8B62, withOpacity(4.0).fillArgb());
        assertEquals(0x012E8B62, withOpacity(-1.0).fillArgb());
    }
}
