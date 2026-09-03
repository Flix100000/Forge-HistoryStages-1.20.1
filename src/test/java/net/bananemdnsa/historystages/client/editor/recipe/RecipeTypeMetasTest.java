package net.bananemdnsa.historystages.client.editor.recipe;

import net.bananemdnsa.historystages.api.editor.RecipeTypeMeta;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a recipe type looks like in the editor: which block stands for it, what colour its accent
 * is, what it is called. This used to be three private if-chains over the vanilla constants, in
 * two classes, whose colours had already drifted apart.
 */
class RecipeTypeMetasTest {

    @AfterEach
    void resetRegistry() {
        RecipeTypeMetas.resetForTesting();
    }

    @Test
    void theVanillaTypesAreSeeded() {
        assertEquals("minecraft:crafting_table",
                RecipeTypeMetas.get("minecraft:crafting").workstationItemId());
        assertEquals("minecraft:furnace",
                RecipeTypeMetas.get("minecraft:smelting").workstationItemId());
        assertEquals("minecraft:smithing_table",
                RecipeTypeMetas.get("minecraft:smithing").workstationItemId());
    }

    @Test
    void everyVanillaTypeHasItsOwnAccentColour() {
        // The two hand-kept colour tables disagreed about smelting and campfire before this
        // registry existed. One table, one answer.
        assertNotEquals(RecipeTypeMetas.get("minecraft:smelting").accentColor(),
                RecipeTypeMetas.get("minecraft:blasting").accentColor());
    }

    @Test
    void anUnknownTypeFallsBackToItsOwnId() {
        RecipeTypeMeta meta = RecipeTypeMetas.get("create:mixing");
        assertEquals("create:mixing", meta.displayFallback());
        assertEquals("", meta.workstationItemId());
    }

    @Test
    void anAddonCanRegisterItsOwnType() {
        RecipeTypeMetas.register(new RecipeTypeMeta(
                "create:mixing", "create:basin", 0xFF3399FF, "recipe_type.create.mixing"));
        RecipeTypeMeta meta = RecipeTypeMetas.get("create:mixing");
        assertEquals("create:basin", meta.workstationItemId());
        assertEquals(0xFF3399FF, meta.accentColor());
        assertEquals("recipe_type.create.mixing", meta.nameLangKey());
    }

    @Test
    void registeringTwiceUnderTheSameIdIsRejected() {
        RecipeTypeMetas.register(new RecipeTypeMeta(
                "create:mixing", "create:basin", 0xFF3399FF, "recipe_type.create.mixing"));
        assertThrows(IllegalArgumentException.class, () -> RecipeTypeMetas.register(
                new RecipeTypeMeta("create:mixing", "create:basin", 0xFF000000, "other")));
    }

    @Test
    void anAddonCannotOverwriteAVanillaType() {
        assertThrows(IllegalArgumentException.class, () -> RecipeTypeMetas.register(
                new RecipeTypeMeta("minecraft:crafting", "mymod:bench", 0xFF000000, "x")));
    }

    @Test
    void registrationClosesAtTheFreeze() {
        RecipeTypeMetas.freeze();
        assertTrue(RecipeTypeMetas.isFrozen());
        assertThrows(IllegalStateException.class, () -> RecipeTypeMetas.register(
                new RecipeTypeMeta("create:mixing", "create:basin", 0xFF3399FF, "x")));
    }

    @Test
    void freezingTwiceIsHarmless() {
        RecipeTypeMetas.freeze();
        RecipeTypeMetas.freeze();
        assertTrue(RecipeTypeMetas.isFrozen());
    }

    @Test
    void gettingAnUnknownTypeNeverReturnsNull() {
        // Callers draw a card with whatever comes back; a null here would be a crash mid-frame.
        assertEquals("", RecipeTypeMetas.get(null).workstationItemId());
        assertEquals("", RecipeTypeMetas.get("").workstationItemId());
    }

    @Test
    void everyBuiltInCarriesALangKeyRatherThanAHardcodedName() {
        // The table this replaces returned literal English straight into the screen.
        for (RecipeTypeMeta meta : RecipeTypeMetas.all()) {
            assertTrue(meta.nameLangKey().startsWith("editor.historystages.recipe_type."),
                    meta.typeId() + " must name a lang key, not carry a display string");
        }
    }
}
