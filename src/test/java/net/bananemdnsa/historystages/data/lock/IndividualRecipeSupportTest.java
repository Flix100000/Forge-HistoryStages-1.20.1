package net.bananemdnsa.historystages.data.lock;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two readers ask this — the editor's recipe picker and the load-time audit — and they have to get
 * the same answer. Two hand-kept lists of the same fact have drifted apart twice in this repo.
 */
class IndividualRecipeSupportTest {

    @Test
    void theThreeStationsWithAPlayerInFrontOfThemAreSupported() {
        assertTrue(IndividualRecipeSupport.supports("minecraft:crafting"));
        assertTrue(IndividualRecipeSupport.supports("minecraft:stonecutting"));
        assertTrue(IndividualRecipeSupport.supports("minecraft:smithing"));
    }

    @Test
    void theCookingTypesRunInABlockEntityAndAreNot() {
        assertFalse(IndividualRecipeSupport.supports("minecraft:smelting"));
        assertFalse(IndividualRecipeSupport.supports("minecraft:blasting"));
        assertFalse(IndividualRecipeSupport.supports("minecraft:smoking"));
        assertFalse(IndividualRecipeSupport.supports("minecraft:campfire_cooking"));
    }

    @Test
    void aModTypeIsNotAssumedToBeSupported() {
        assertFalse(IndividualRecipeSupport.supports("create:mixing"));
    }

    @Test
    void anUnknownOrMissingTypeIsNotSupported() {
        assertFalse(IndividualRecipeSupport.supports(null));
        assertFalse(IndividualRecipeSupport.supports(""));
        assertFalse(IndividualRecipeSupport.supports("crafting"));
    }

    @Test
    void theSetIsExactlyWhatTheHooksCover() {
        assertEquals(
                Set.of("minecraft:crafting", "minecraft:stonecutting", "minecraft:smithing"),
                IndividualRecipeSupport.SUPPORTED_TYPE_IDS,
                "adding a type here without adding the matching menu hook makes the picker lie");
    }
}
