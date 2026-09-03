package net.bananemdnsa.historystages.data.lock;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule that decides which fluids a recipe touches, and on which side.
 *
 * <p>Deliberately a pure JSON question with the registries handed in as predicates: there is no
 * standard for how a mod spells a fluid ingredient, so the only way to be sure this copes with
 * Create, Mekanism and Thermal at once is to feed it their actual shapes.
 */
class FluidRecipeScannerTest {

    private static final Predicate<String> FLUIDS = id -> Set.of(
            "minecraft:water", "minecraft:lava",
            "create:molten_iron", "mekanism:hydrogen", "thermal:crude_oil").contains(id);

    /** Deliberately overlapping with the fluids: "foo:oil" exists as both, which is the trap. */
    private static final Predicate<String> ITEMS = id -> Set.of(
            "minecraft:iron_ingot", "minecraft:bucket", "thermal:crude_oil").contains(id);

    private static Map<String, Set<FluidRecipeScanner.Position>> scan(String json) {
        JsonElement parsed = JsonParser.parseString(json);
        return FluidRecipeScanner.scan(parsed, FLUIDS, ITEMS);
    }

    // ---- the three shapes that exist in the wild ------------------------------------

    @Test
    void createNamesItsSidesIngredientsAndResults() {
        var found = scan("""
                { "type": "create:mixing",
                  "ingredients": [{"item": "minecraft:iron_ingot"},
                                  {"fluid": "minecraft:lava", "amount": 1000}],
                  "results":     [{"fluid": "create:molten_iron", "amount": 100}] }
                """);

        assertEquals(Set.of(FluidRecipeScanner.Position.INPUT), found.get("minecraft:lava"));
        assertEquals(Set.of(FluidRecipeScanner.Position.OUTPUT), found.get("create:molten_iron"));
        assertEquals(2, found.size());
    }

    @Test
    void mekanismNamesThemInputAndOutput() {
        var found = scan("""
                { "type": "mekanism:dissolution",
                  "input":  {"fluid": {"fluid": "minecraft:water", "amount": 100}},
                  "output": {"fluid": {"fluid": "mekanism:hydrogen", "amount": 100}} }
                """);

        assertEquals(Set.of(FluidRecipeScanner.Position.INPUT), found.get("minecraft:water"));
        assertEquals(Set.of(FluidRecipeScanner.Position.OUTPUT), found.get("mekanism:hydrogen"));
    }

    @Test
    void thermalUsesTheSingularResult() {
        var found = scan("""
                { "type": "thermal:refinery",
                  "ingredient": {"fluid": "thermal:crude_oil", "amount": 100},
                  "result": [{"fluid": "minecraft:lava", "amount": 50}] }
                """);

        assertEquals(Set.of(FluidRecipeScanner.Position.INPUT), found.get("thermal:crude_oil"));
        assertEquals(Set.of(FluidRecipeScanner.Position.OUTPUT), found.get("minecraft:lava"));
    }

    // ---- the fallback ----------------------------------------------------------------

    @Test
    void anUnknownSpellingLandsInUnknownRatherThanBeingDropped() {
        var found = scan("""
                { "type": "somemod:thing",
                  "theStuffItNeeds": [{"fluid": "minecraft:lava", "amount": 1}] }
                """);

        assertEquals(Set.of(FluidRecipeScanner.Position.UNKNOWN), found.get("minecraft:lava"));
    }

    @Test
    void aFluidAtTheTopLevelIsUnknownTooRatherThanGuessed() {
        var found = scan("{ \"type\": \"somemod:thing\", \"fluid\": \"minecraft:lava\" }");
        assertEquals(Set.of(FluidRecipeScanner.Position.UNKNOWN), found.get("minecraft:lava"));
    }

    // ---- what must NOT be picked up ---------------------------------------------------

    @Test
    void anItemThatSharesItsNameWithAFluidIsNotAFluidReference() {
        // thermal:crude_oil is registered as both. Under an item key it is the item.
        var found = scan("""
                { "type": "minecraft:crafting_shapeless",
                  "ingredients": [{"item": "thermal:crude_oil"}] }
                """);

        assertTrue(found.isEmpty(), "an item key must not be read as a fluid: " + found);
    }

    @Test
    void aPlainItemRecipeTouchesNoFluid() {
        var found = scan("""
                { "type": "minecraft:crafting_shapeless",
                  "ingredients": [{"item": "minecraft:iron_ingot"}],
                  "result": {"id": "minecraft:bucket", "count": 1} }
                """);

        assertTrue(found.isEmpty(), "found " + found);
    }

    @Test
    void aStringThatIsNoRegisteredFluidIsIgnored() {
        var found = scan("{ \"fluid\": \"somemod:unknown_goo\" }");
        assertTrue(found.isEmpty(), "found " + found);
    }

    // ---- merging ----------------------------------------------------------------------

    @Test
    void aFluidOnBothSidesKeepsBothPositions() {
        var found = scan("""
                { "type": "create:mixing",
                  "ingredients": [{"fluid": "minecraft:water", "amount": 1000}],
                  "results":     [{"fluid": "minecraft:water", "amount": 500}] }
                """);

        assertEquals(Set.of(FluidRecipeScanner.Position.INPUT, FluidRecipeScanner.Position.OUTPUT),
                found.get("minecraft:water"));
    }

    @Test
    void nestingDeeperDoesNotLoseTheSide() {
        var found = scan("""
                { "type": "somemod:multiblock",
                  "results": {"primary": {"stack": {"fluid": "minecraft:lava", "amount": 1}}} }
                """);

        assertEquals(Set.of(FluidRecipeScanner.Position.OUTPUT), found.get("minecraft:lava"));
    }

    @Test
    void nullAndNonObjectInputAreHandled() {
        assertTrue(FluidRecipeScanner.scan(null, FLUIDS, ITEMS).isEmpty());
        assertTrue(scan("\"just a string\"").isEmpty());
        assertTrue(scan("[]").isEmpty());
    }
}
