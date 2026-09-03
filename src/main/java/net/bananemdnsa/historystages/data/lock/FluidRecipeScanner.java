package net.bananemdnsa.historystages.data.lock;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Which fluids a recipe mentions, and on which side.
 *
 * <p>There is no accessor for this. A recipe's item result is reachable through
 * {@code getResultItem}, but a fluid result belongs to whatever type the owning mod invented, and
 * NeoForge defines no common interface for it. What every one of them does share is the
 * serialised form — so this reads that, and stays mod-agnostic by never naming a recipe type.
 *
 * <p>Deliberately free of Minecraft: the registries arrive as predicates. That is what lets the
 * rule below be pinned against Create's, Mekanism's and Thermal's real shapes in a unit test
 * rather than only in game, which matters because the rule is a heuristic and heuristics rot
 * quietly.
 */
public final class FluidRecipeScanner {

    /** Which side of a recipe a fluid reference sits on. */
    public enum Position { INPUT, OUTPUT, UNKNOWN }

    /**
     * Key names that settle the side. No standard exists — Create writes {@code ingredients} and
     * {@code results}, Mekanism {@code input} and {@code output}, Thermal {@code ingredient} and
     * {@code result} — so this is a list of what mods actually use, not a specification.
     */
    private static final Set<String> OUTPUT_KEYS =
            Set.of("result", "results", "output", "outputs", "production", "produces");

    private static final Set<String> INPUT_KEYS =
            Set.of("ingredient", "ingredients", "input", "inputs", "consumes", "consumed");

    private FluidRecipeScanner() {}

    /**
     * Every fluid this recipe mentions, mapped to the sides it was found on.
     *
     * @param recipe    the recipe's serialised form
     * @param isFluid   whether an id names a registered fluid
     * @param isItem    whether an id names a registered item — needed because some ids are both,
     *                  and then the surrounding key is the only thing that tells them apart
     */
    public static Map<String, Set<Position>> scan(JsonElement recipe,
                                                  Predicate<String> isFluid,
                                                  Predicate<String> isItem) {
        Map<String, Set<Position>> found = new HashMap<>();
        if (recipe != null) walk(recipe, Position.UNKNOWN, false, isFluid, isItem, found);
        return found;
    }

    /**
     * @param side        the side settled by the nearest classifying ancestor key
     * @param underFluid  whether some ancestor key named a fluid, which is what makes a bare id
     *                    string readable as one
     */
    private static void walk(JsonElement element, Position side, boolean underFluid,
                             Predicate<String> isFluid, Predicate<String> isItem,
                             Map<String, Set<Position>> found) {
        if (element instanceof JsonObject object) {
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                walk(entry.getValue(), sideFor(key, side), underFluid || namesAFluid(key),
                        isFluid, isItem, found);
            }
            return;
        }
        if (element instanceof JsonArray array) {
            for (JsonElement child : array) {
                walk(child, side, underFluid, isFluid, isItem, found);
            }
            return;
        }
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) return;

        String value = primitive.getAsString();
        if (!isFluid.test(value)) return;

        // Under a fluid-named key the id is a fluid by construction. Elsewhere it only counts
        // when nothing else could claim it — an id registered as an item too is the item there,
        // which is what keeps a shapeless recipe for thermal:crude_oil out of the fluid index.
        if (!underFluid && isItem.test(value)) return;

        found.computeIfAbsent(value, k -> EnumSet.noneOf(Position.class)).add(side);
    }

    /** A key that settles the side wins; anything else leaves the ancestor's answer standing. */
    private static Position sideFor(String key, Position inherited) {
        if (OUTPUT_KEYS.contains(key)) return Position.OUTPUT;
        if (INPUT_KEYS.contains(key)) return Position.INPUT;
        return inherited;
    }

    /** {@code fluid}, {@code fluidTag}, {@code fluid_ingredient} — anything spelling it out. */
    private static boolean namesAFluid(String key) {
        return key.contains("fluid");
    }
}
