package net.bananemdnsa.historystages.data.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraphColorsTest {

    @Test
    void parsesSixDigitsWithHash() {
        assertEquals(0x44CC99, GraphColors.parse("#44CC99", -1));
    }

    @Test
    void parsesWithoutHashAndLowercase() {
        assertEquals(0x44cc99, GraphColors.parse("44cc99", -1));
    }

    @Test
    void rejectsShortValues() {
        assertEquals(-1, GraphColors.parse("#FFF", -1));
        assertEquals(-1, GraphColors.parse("F", -1));
    }

    @Test
    void rejectsNonHexAndNull() {
        assertEquals(-1, GraphColors.parse("#GGGGGG", -1));
        assertEquals(-1, GraphColors.parse("", -1));
        assertEquals(-1, GraphColors.parse(null, -1));
    }

    @Test
    void formatsUppercaseWithHash() {
        assertEquals("#44CC99", GraphColors.format(0x44CC99));
        assertEquals("#000000", GraphColors.format(0));
        assertEquals("#0A0B0C", GraphColors.format(0x0A0B0C));
    }

    @Test
    void formatIgnoresAlphaBits() {
        assertEquals("#44CC99", GraphColors.format(0xFF44CC99));
    }

    @Test
    void roundTrips() {
        for (String s : new String[]{"#000000", "#FFFFFF", "#123456", "#ABCDEF"}) {
            assertEquals(s, GraphColors.format(GraphColors.parse(s, -1)), s);
        }
    }

    @Test
    void isValidMatchesParse() {
        assertTrue(GraphColors.isValid("#44CC99"));
        assertTrue(GraphColors.isValid("44cc99"));
        assertFalse(GraphColors.isValid("#FFF"));
        assertFalse(GraphColors.isValid(null));
    }
}
