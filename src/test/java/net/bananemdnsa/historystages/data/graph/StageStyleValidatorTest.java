package net.bananemdnsa.historystages.data.graph;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StageStyleValidatorTest {

    /**
     * Stands in for what GraphConfigEntries.styleKeys returns at runtime. Built by hand because
     * ModConfigSpec is not on the test classpath — build.gradle:212 keeps NeoForge out of the
     * test source set.
     */
    private static List<GraphKey> keys() {
        return List.of(
                new GraphKey("style.global.locked.shape", GraphKey.Kind.ENUM, "ROUNDED",
                        null, null, List.of("RECT", "ROUNDED", "CIRCLE", "DIAMOND", "HEXAGON"),
                        "NodeShape"),
                new GraphKey("style.global.locked.size", GraphKey.Kind.DOUBLE, "1.0",
                        0.25, 4.0, List.of(), null),
                new GraphKey("style.global.locked.borderWidth", GraphKey.Kind.INTEGER, "2",
                        1.0, 5.0, List.of(), null),
                new GraphKey("style.global.locked.fill", GraphKey.Kind.COLOR, "#2E8B62",
                        null, null, List.of(), null),
                new GraphKey("style.global.locked.checkmark", GraphKey.Kind.BOOLEAN, "false",
                        null, null, List.of(), null));
    }

    @Test
    void aValidStylePassesThroughUnchanged() {
        StageStyle in = new StageStyle();
        in.shape = "HEXAGON";
        in.size = 1.5;
        in.fill = "#C04040";

        StageStyle out = StageStyleValidator.sanitize(in, keys());

        assertEquals("HEXAGON", out.shape);
        assertEquals(1.5, out.size);
        assertEquals("#C04040", out.fill);
    }

    @Test
    void anOutOfRangeNumberIsDropped() {
        StageStyle in = new StageStyle();
        in.size = 400.0;

        assertNull(StageStyleValidator.sanitize(in, keys()).size);
    }

    @Test
    void nonFiniteNumbersAreDropped() {
        // NaN compares false against both bounds, so it passes a plain range check — and Gson
        // accepts NaN and "NaN" into a Double field, so a modified client can send one.
        StageStyle nan = new StageStyle();
        nan.size = Double.NaN;
        assertNull(StageStyleValidator.sanitize(nan, keys()).size);

        StageStyle infinite = new StageStyle();
        infinite.size = Double.POSITIVE_INFINITY;
        assertNull(StageStyleValidator.sanitize(infinite, keys()).size);

        StageStyle negativeInfinite = new StageStyle();
        negativeInfinite.size = Double.NEGATIVE_INFINITY;
        assertNull(StageStyleValidator.sanitize(negativeInfinite, keys()).size);
    }

    @Test
    void aBadColourIsDropped() {
        StageStyle in = new StageStyle();
        in.fill = "rgb(1,2,3)";

        assertNull(StageStyleValidator.sanitize(in, keys()).fill);
    }

    @Test
    void anUnknownEnumConstantIsDropped() {
        StageStyle in = new StageStyle();
        in.shape = "TRIANGLE";

        assertNull(StageStyleValidator.sanitize(in, keys()).shape);
    }

    @Test
    void enumConstantsAreCaseInsensitiveAndNormalised() {
        StageStyle in = new StageStyle();
        in.shape = "hexagon";

        assertEquals("HEXAGON", StageStyleValidator.sanitize(in, keys()).shape,
                "a pack file may write lower case; the stored value should not");
    }

    @Test
    void oneBadFieldDoesNotTakeTheRestDown() {
        StageStyle in = new StageStyle();
        in.size = 400.0;
        in.fill = "#C04040";
        in.checkmark = Boolean.TRUE;

        StageStyle out = StageStyleValidator.sanitize(in, keys());

        assertNull(out.size);
        assertEquals("#C04040", out.fill);
        assertEquals(Boolean.TRUE, out.checkmark);
    }

    @Test
    void aLeafWithNoKeyIsDropped() {
        // cornerRadius is filtered out of the editable set by GraphConfigEntries.HIDDEN_LEAVES,
        // so nothing may write it through this path.
        StageStyle in = new StageStyle();
        in.cornerRadius = 9;

        assertNull(StageStyleValidator.sanitize(in, keys()).cornerRadius);
    }

    @Test
    void nullInputGivesAnEmptyStyle() {
        assertTrue(StageStyleValidator.sanitize(null, keys()).isEmpty());
    }
}
