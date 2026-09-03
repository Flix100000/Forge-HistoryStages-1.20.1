package net.bananemdnsa.historystages.client.editor.recipe;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bananemdnsa.historystages.data.lock.FluidRecipeScanner.Position;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which of a recipe's fluids the picker shows, and where.
 *
 * <p>The two sides answer differently on purpose. The lock counts an unclassified fluid as both
 * sides, erring towards one recipe too many. In the left column, which is navigated by, that same
 * erring would be a lie — so an output has to be certain. On a card it is the opposite: the card
 * has to show what the lock acts on, or the editor knows less than the gate.
 */
class RecipeFluidsTest {

    /** Builds a side map in a fixed order, so the assertions can compare lists. */
    private static final class Sides {
        private final Map<String, Set<Position>> map = new LinkedHashMap<>();

        Sides and(String fluidId, Position... found) {
            map.put(fluidId, EnumSet.copyOf(List.of(found)));
            return this;
        }

        Map<String, Set<Position>> build() {
            return map;
        }
    }

    private static Sides sides() {
        return new Sides();
    }

    @Test
    void aDefiniteOutputCounts() {
        assertEquals(List.of("create:molten_iron"), RecipeFluids.definiteOutputs(
                sides().and("create:molten_iron", Position.OUTPUT).build()));
    }

    @Test
    void anInputIsNotAnOutput() {
        assertTrue(RecipeFluids.definiteOutputs(
                sides().and("minecraft:lava", Position.INPUT).build()).isEmpty());
    }

    @Test
    void anUnclassifiedFluidIsNotAnOutput() {
        // The whole point of the maintainer's "only the certain ones" call: a fluid we could not
        // place must not put a recipe under an entry that does not make it.
        assertTrue(RecipeFluids.definiteOutputs(
                sides().and("minecraft:lava", Position.UNKNOWN).build()).isEmpty());
    }

    @Test
    void aFluidFoundOnBothSidesStillCountsAsAnOutput() {
        // A machine that takes water and also gives water back is still a way to get water.
        assertEquals(List.of("minecraft:water"), RecipeFluids.definiteOutputs(
                sides().and("minecraft:water", Position.INPUT, Position.OUTPUT).build()));
    }

    @Test
    void aPossibleOutputCoversTheCertainAndTheUnreadable() {
        // Both go in the master grid. A recipe left out of it is reachable nowhere else in the
        // editor at all, so a marked guess beats an absent entry.
        assertEquals(List.of("create:molten_iron", "mystery:goo"),
                RecipeFluids.possibleOutputs(sides()
                        .and("create:molten_iron", Position.OUTPUT)
                        .and("mystery:goo", Position.UNKNOWN).build()));
    }

    @Test
    void aDefiniteInputIsNeverAPossibleOutput() {
        // The one case where the answer is actually known. Listing a consumer among the
        // producers is the lie this whole split exists to avoid.
        assertTrue(RecipeFluids.possibleOutputs(
                sides().and("minecraft:lava", Position.INPUT).build()).isEmpty());
    }

    @Test
    void aFluidOnBothSidesIsStillAPossibleOutput() {
        assertEquals(List.of("minecraft:water"), RecipeFluids.possibleOutputs(
                sides().and("minecraft:water", Position.INPUT, Position.OUTPUT).build()));
    }

    @Test
    void theIngredientRowCarriesInputsAndUnclassifiedOnes() {
        List<RecipeFluids.Ref> row = RecipeFluids.ingredientRow(sides()
                .and("minecraft:lava", Position.INPUT)
                .and("create:molten_iron", Position.OUTPUT)
                .and("mystery:goo", Position.UNKNOWN).build());
        assertEquals(List.of("minecraft:lava", "mystery:goo"),
                row.stream().map(RecipeFluids.Ref::fluidId).toList());
    }

    @Test
    void theRowMarksWhichOnesWeCouldNotPlace() {
        List<RecipeFluids.Ref> row = RecipeFluids.ingredientRow(sides()
                .and("minecraft:lava", Position.INPUT)
                .and("mystery:goo", Position.UNKNOWN).build());
        assertTrue(row.get(0).sideKnown());
        assertFalse(row.get(1).sideKnown());
    }

    @Test
    void aFluidOnBothSidesIsAKnownIngredient() {
        List<RecipeFluids.Ref> row = RecipeFluids.ingredientRow(
                sides().and("minecraft:water", Position.INPUT, Position.OUTPUT).build());
        assertEquals(1, row.size());
        assertTrue(row.get(0).sideKnown());
    }

    @Test
    void anEmptyMapGivesEmptyAnswers() {
        assertTrue(RecipeFluids.definiteOutputs(Map.of()).isEmpty());
        assertTrue(RecipeFluids.ingredientRow(Map.of()).isEmpty());
    }
}
