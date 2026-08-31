package net.bananemdnsa.historystages.data.dependency;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which modes each scope may spell an individual-stage prerequisite with.
 *
 * <p>{@code player} asks about whoever is researching. On an individual stage that is the whole
 * point — the stage unlocks for that player and nobody else. On a global stage the gate would be
 * personal while the reward is not: the first qualifying player opens the stage for the entire
 * server, including everyone without the prerequisite. It is therefore not offered there, and
 * this is the rule rather than the dropdown, because three places read it — the picker, the
 * context menu's cycle, and the loader that corrects a file carrying the wrong one.
 */
class IndividualStageDepModesTest {

    @Test
    void anIndividualStageMayDemandTheStageOfTheResearcherAlone() {
        assertTrue(IndividualStageDep.modesFor(true).contains(IndividualStageDep.MODE_PLAYER),
                "the player mode is what an individual-to-individual prerequisite is for;"
                        + " without it such a chain has no first player who can start it");
    }

    @Test
    void aGlobalStageMayNot() {
        assertFalse(IndividualStageDep.modesFor(false).contains(IndividualStageDep.MODE_PLAYER),
                "a global stage unlocks once for everybody, so a personal gate on it would let one"
                        + " qualifying player open it for players who hold nothing");
    }

    @Test
    void bothScopesKeepTheServerWideModes() {
        for (boolean individual : new boolean[] { true, false }) {
            List<String> modes = IndividualStageDep.modesFor(individual);
            assertTrue(modes.contains(IndividualStageDep.MODE_ALL_ONLINE),
                    "all_online missing for individual=" + individual);
            assertTrue(modes.contains(IndividualStageDep.MODE_ALL_EVER),
                    "all_ever missing for individual=" + individual);
        }
    }

    /**
     * The two lists cannot drift apart: anything a picker offers has to survive the loader, or the
     * editor would write a mode that the next start silently resets.
     */
    @Test
    void everyOfferedModeIsOneTheLoaderAccepts() {
        for (boolean individual : new boolean[] { true, false }) {
            for (String mode : IndividualStageDep.modesFor(individual)) {
                assertTrue(IndividualStageDep.isValidMode(mode),
                        "mode '" + mode + "' is offered for individual=" + individual
                                + " but the loader would reject it");
            }
        }
    }
}
