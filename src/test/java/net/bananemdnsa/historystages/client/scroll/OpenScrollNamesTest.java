package net.bananemdnsa.historystages.client.scroll;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Only {@link OpenScrollNames#prettify} is exercised here: the other methods reach into the
 * Minecraft registries and the language manager, neither of which exists without a running game.
 */
class OpenScrollNamesTest {

    @Test
    void underscoresBecomeSpacesAndEveryWordIsCapitalised() {
        assertEquals("Old Growth Taiga", OpenScrollNames.prettify("old_growth_taiga"));
        assertEquals("The Nether", OpenScrollNames.prettify("the_nether"));
    }

    @Test
    void aNamespaceIsDroppedBeforePrettifying() {
        assertEquals("Mystic Grove", OpenScrollNames.prettify("biomesoplenty:mystic_grove"));
    }

    @Test
    void aSingleWordIsJustCapitalised() {
        assertEquals("Plains", OpenScrollNames.prettify("plains"));
    }

    @Test
    void alreadyCapitalisedTextSurvivesUnharmed() {
        assertEquals("Plains", OpenScrollNames.prettify("Plains"));
    }

    @Test
    void emptyAndNullDoNotThrow() {
        assertEquals("", OpenScrollNames.prettify(""));
        assertEquals("", OpenScrollNames.prettify(null));
        assertEquals("", OpenScrollNames.prettify("___"));
    }

    @Test
    void digitsAndRunsOfUnderscoresDoNotProduceDoubleSpaces() {
        assertEquals("Ruined Portal 2", OpenScrollNames.prettify("ruined__portal_2"));
    }
}
