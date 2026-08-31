package net.bananemdnsa.historystages.compat;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage ids are author-chosen and this mod allows what a resource location does not.
 *
 * <p>One uppercase letter in one stage id used to throw out of {@code JEIPlugin.registerRecipes},
 * which aborted the registration before {@code addRecipes} ran — so <em>every</em> reseal entry
 * disappeared from the viewer, not just the offending one. EMI had the identical line.
 */
class StageDisplayPathTest {

    /** The characters {@code ResourceLocation.assertValidPath} accepts. */
    private static final Pattern VALID_PATH = Pattern.compile("[a-z0-9/._-]*");

    private static void assertValid(String path) {
        assertTrue(VALID_PATH.matcher(path).matches(),
                "'" + path + "' would be refused by ResourceLocation.assertValidPath");
    }

    @Test
    void anIdThatIsAlreadyValidIsLeftAlone() {
        // The common case. Rewriting it would make every id in the viewers and the logs unreadable
        // for the sake of the rare broken one.
        assertEquals("bronze_age", StageDisplayPath.of("bronze_age"));
        assertEquals("chapter-1.2", StageDisplayPath.of("chapter-1.2"));
    }

    @Test
    void anUppercaseIdBecomesAValidPath() {
        assertValid(StageDisplayPath.of("StageManagerTest"));
    }

    @Test
    void aColonBecomesAValidPath() {
        assertValid(StageDisplayPath.of("gametest:locked_item"));
    }

    @Test
    void spacesAndPunctuationBecomeAValidPath() {
        assertValid(StageDisplayPath.of("Kapitel 3 (Eisen!)"));
        assertValid(StageDisplayPath.of("stufe#4@ende"));
    }

    @Test
    void twoIdsDifferingOnlyInCaseDoNotCollide() {
        // Both fold to "bronze". Without something to tell them apart, two stages would hand the
        // viewer two entries under one id.
        assertNotEquals(StageDisplayPath.of("Bronze"), StageDisplayPath.of("bronze"));
    }

    @Test
    void twoDifferentBrokenIdsDoNotCollide() {
        assertNotEquals(StageDisplayPath.of("a:b"), StageDisplayPath.of("a_b"));
        assertNotEquals(StageDisplayPath.of("Eisen Zeit"), StageDisplayPath.of("Eisen-Zeit"));
    }

    @Test
    void theSameIdAlwaysGivesTheSamePath() {
        assertEquals(StageDisplayPath.of("StageManagerTest"), StageDisplayPath.of("StageManagerTest"));
    }

    @Test
    void anEmptyOrMissingIdDoesNotThrow() {
        assertValid(StageDisplayPath.of(""));
        assertValid(StageDisplayPath.of(null));
    }
}
