package net.bananemdnsa.historystages.client.editor.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bananemdnsa.historystages.data.lock.FluidRecipeScanner.Position;

/**
 * Reads a recipe's fluid side map the way the picker needs it.
 *
 * <p>The two questions get different answers on purpose. An entry in the left column claims "this
 * recipe makes that fluid", and a claim has to be certain — an unclassified fluid would file a
 * recipe under something it may only consume, and that column is what one navigates by. The card
 * is the opposite: it has to show every fluid the lock acts on, including the ones whose side we
 * could not read, or the editor would know less than the gate it exists to explain.
 *
 * <p>No Minecraft type appears here, which is what lets both rules be pinned by a test.
 */
public final class RecipeFluids {

    /** One fluid on a card's ingredient row. */
    public record Ref(String fluidId, boolean sideKnown) {
    }

    private RecipeFluids() {
    }

    /** The fluids this recipe certainly produces. Insertion order is kept. */
    public static List<String> definiteOutputs(Map<String, Set<Position>> sides) {
        List<String> outputs = new ArrayList<>();
        for (Map.Entry<String, Set<Position>> entry : sides.entrySet()) {
            if (entry.getValue().contains(Position.OUTPUT)) outputs.add(entry.getKey());
        }
        return outputs;
    }

    /**
     * What belongs on the card's ingredient row: everything read as an input, plus everything we
     * could not place — the lock gates on those too, so leaving them off would hide the reason a
     * recipe disappeared.
     */
    public static List<Ref> ingredientRow(Map<String, Set<Position>> sides) {
        List<Ref> row = new ArrayList<>();
        for (Map.Entry<String, Set<Position>> entry : sides.entrySet()) {
            Set<Position> found = entry.getValue();
            if (found.contains(Position.INPUT)) {
                row.add(new Ref(entry.getKey(), true));
            } else if (found.contains(Position.UNKNOWN)) {
                row.add(new Ref(entry.getKey(), false));
            }
        }
        return row;
    }
}
