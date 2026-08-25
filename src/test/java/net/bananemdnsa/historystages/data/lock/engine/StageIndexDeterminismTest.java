package net.bananemdnsa.historystages.data.lock.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import net.bananemdnsa.historystages.api.stage.StageStateView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The numbering has to come out identical on the server and on every client, or the mod lies.
 *
 * <p>This is the one failure in the whole rework that does not throw, does not log, and does not
 * look like a bug: a client that numbers its stages differently reads someone else's bits, and
 * shows an item as free that the server blocks — or the other way round. It presents as "the mod
 * is broken for one player", which is unfindable without knowing to look here.
 *
 * <p>So the numbering is derived from a sort, never from map order, and never stored. These tests
 * hold both halves of that.
 */
class StageIndexDeterminismTest {

    private static List<String> names(String prefix, int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) ids.add(prefix + "_" + i);
        return ids;
    }

    @Test
    void theSameStagesInAnyOrderNumberTheSame() {
        // The stage maps are ConcurrentHashMaps. Their iteration order is not stable across runs
        // and not equal between two machines holding the same stages, which is the whole reason
        // the index sorts instead of taking what it is handed.
        List<String> globals = names("bronze", 40);
        List<String> individuals = names("quest", 25);

        StageIndex first = StageIndex.of(globals, individuals);

        Random shuffle = new Random(1234);
        for (int attempt = 0; attempt < 20; attempt++) {
            List<String> g = new ArrayList<>(globals);
            List<String> i = new ArrayList<>(individuals);
            Collections.shuffle(g, shuffle);
            Collections.shuffle(i, shuffle);

            StageIndex other = StageIndex.of(g, i);
            for (String id : globals) {
                assertEquals(first.numberOf(id), other.numberOf(id),
                        id + " was numbered differently after shuffling the input");
            }
            for (String id : individuals) {
                assertEquals(first.numberOf(id), other.numberOf(id),
                        id + " was numbered differently after shuffling the input");
            }
        }
    }

    @Test
    void aSetRatherThanAListNumbersTheSame() {
        // The real caller hands over a keySet, whose order is arbitrary.
        List<String> globals = names("bronze", 30);
        Set<String> asSet = new LinkedHashSet<>(globals);
        Collections.reverse(globals);

        StageIndex fromList = StageIndex.of(globals, List.of());
        StageIndex fromSet = StageIndex.of(asSet, List.of());

        for (String id : asSet) {
            assertEquals(fromSet.numberOf(id), fromList.numberOf(id), id);
        }
    }

    @Test
    void globalStagesComeBeforeIndividualOnes() {
        StageIndex index = StageIndex.of(List.of("zzz_global"), List.of("aaa_individual"));
        assertTrue(index.numberOf("zzz_global") < index.numberOf("aaa_individual"),
                "globals must sort first, so adding an individual stage cannot renumber them");
    }

    @Test
    void addingAnIndividualStageLeavesTheGlobalNumbersAlone() {
        List<String> globals = names("bronze", 20);
        StageIndex before = StageIndex.of(globals, List.of("quest_a"));
        StageIndex after = StageIndex.of(globals, List.of("quest_a", "quest_b"));

        for (String id : globals) {
            assertEquals(before.numberOf(id), after.numberOf(id),
                    id + " was renumbered by an unrelated individual stage");
        }
    }

    @Test
    void nameOrderIsCaseInsensitive() {
        StageIndex index = StageIndex.of(List.of("Bronze", "apple", "Cherry"), List.of());
        assertTrue(index.numberOf("apple") < index.numberOf("Bronze"));
        assertTrue(index.numberOf("Bronze") < index.numberOf("Cherry"));
    }

    @Test
    void anUnknownStageHasNoNumber() {
        StageIndex index = StageIndex.of(List.of("bronze"), List.of());
        assertEquals(-1, index.numberOf("never_heard_of_it"));
    }

    @Test
    void renamingOneStageRenumbersItsNeighbours() {
        // Not a defect — the reason a number is never written down or sent. Renaming "bbb" to
        // "eee" moves it past the others, and everything it passed shifts down by one. A number
        // stored under the old numbering would come back pointing at a different stage, and
        // nothing about that would look wrong.
        StageIndex before = StageIndex.of(List.of("bbb", "ccc", "ddd"), List.of());
        StageIndex after = StageIndex.of(List.of("ccc", "ddd", "eee"), List.of());

        assertEquals(1, before.numberOf("ccc"));
        assertEquals(0, after.numberOf("ccc"));
        assertNotEquals(before.numberOf("ccc"), after.numberOf("ccc"),
                "if this ever stops shifting, the reasoning behind never persisting a number "
                        + "needs rechecking rather than the test relaxing");

        // And the stage that used to hold number 1 now means something else entirely.
        assertEquals("ccc", before.stageAt(1));
        assertEquals("ddd", after.stageAt(1));
    }

    // ---- the mask itself -----------------------------------------------------------

    @Test
    void aMaskAnswersTheSameAsAskingStageByStage() {
        List<String> all = names("stage", 200);
        StageIndex index = StageIndex.of(all, List.of());

        Random random = new Random(99);
        for (int attempt = 0; attempt < 200; attempt++) {
            Set<String> unlocked = new LinkedHashSet<>();
            Set<String> required = new LinkedHashSet<>();
            for (String id : all) {
                if (random.nextInt(3) == 0) unlocked.add(id);
                if (random.nextInt(10) == 0) required.add(id);
            }

            boolean byStrings = LockResolution.isLocked(required, StageStateView.of(unlocked));
            boolean byMask = StageMask.of(index, unlocked).missesAnyOf(StageMask.of(index, required));

            assertEquals(byStrings, byMask,
                    "mask and string paths disagreed for unlocked=" + unlocked.size()
                            + " required=" + required.size());
        }
    }

    @Test
    void aMaskCrossesTheSixtyFourBitWordBoundary() {
        // One long covers 64 stages. A pack runs several hundred, so the multi-word path is the
        // normal one and an off-by-one in the word index would only show past the first sixty-four.
        List<String> all = names("stage", 130);
        StageIndex index = StageIndex.of(all, List.of());

        for (String id : all) {
            StageMask onlyThisOne = StageMask.of(index, List.of(id));
            assertTrue(onlyThisOne.contains(index, id), id + " lost its own bit");
            assertFalse(StageMask.EMPTY.contains(index, id));
            assertTrue(StageMask.EMPTY.missesAnyOf(onlyThisOne), "an empty holder misses " + id);
            assertFalse(onlyThisOne.missesAnyOf(onlyThisOne), id + " should satisfy itself");
        }
    }

    @Test
    void requiringNothingIsNeverLocked() {
        StageIndex index = StageIndex.of(names("stage", 10), List.of());
        assertFalse(StageMask.EMPTY.missesAnyOf(StageMask.EMPTY));
        assertFalse(StageMask.of(index, List.of("stage_1")).missesAnyOf(StageMask.EMPTY));
    }

    @Test
    void aStageTheIndexDoesNotKnowIsSkippedRatherThanFatal() {
        // A client can hold a stage the index has not caught up with. Reading it as "not
        // unlocked" is the safe answer; throwing in a per-frame lock check is not.
        StageIndex index = StageIndex.of(List.of("bronze"), List.of());
        StageMask mask = StageMask.of(index, List.of("bronze", "arrived_later"));
        assertTrue(mask.contains(index, "bronze"));
        assertFalse(mask.contains(index, "arrived_later"));
    }
}
