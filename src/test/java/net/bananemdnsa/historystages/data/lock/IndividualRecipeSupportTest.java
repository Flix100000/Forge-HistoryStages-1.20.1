package net.bananemdnsa.historystages.data.lock;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two readers ask this — the editor's recipe picker and the load-time audit — and they have to get
 * the same answer. Two hand-kept lists of the same fact have drifted apart twice in this repo.
 *
 * <p>Since 6.0.0 the set is a registry rather than a constant, so a mod whose machine has its own
 * menu can declare its type gateable per player. The vanilla three still have to hold.
 */
class IndividualRecipeSupportTest {

    @AfterEach
    void resetRegistry() {
        IndividualRecipeSupport.resetForTesting();
    }

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
    void theBuiltInsAreStillExactlyWhatTheHooksCover() {
        assertEquals(
                Set.of("minecraft:crafting", "minecraft:stonecutting", "minecraft:smithing"),
                Set.copyOf(IndividualRecipeSupport.builtInIds()),
                "adding a type here without adding the matching menu hook makes the picker lie");
    }

    @Test
    void aMachineWithItsOwnMenuCanRegisterItself() {
        IndividualRecipeSupport.register("mymod:assembler");
        assertTrue(IndividualRecipeSupport.supports("mymod:assembler"));
        assertEquals(List.of("mymod:assembler"), IndividualRecipeSupport.addonIds());
    }

    @Test
    void registeringTheSameTypeTwiceIsHarmless() {
        // Two addons can plausibly both claim the same third-party type. Neither should crash.
        IndividualRecipeSupport.register("mymod:assembler");
        IndividualRecipeSupport.register("mymod:assembler");
        assertEquals(1, IndividualRecipeSupport.addonIds().size());
    }

    @Test
    void registeringAVanillaTypeAgainDoesNotMakeItAnAddonType() {
        // builtInIds() and addonIds() partition supportedTypeIds(); a re-registration must not
        // move an id across that line.
        IndividualRecipeSupport.register("minecraft:crafting");
        assertTrue(IndividualRecipeSupport.addonIds().isEmpty());
        assertEquals(3, IndividualRecipeSupport.supportedTypeIds().size());
    }

    @Test
    void registrationClosesAtTheFreeze() {
        IndividualRecipeSupport.freeze();
        assertTrue(IndividualRecipeSupport.isFrozen());
        assertThrows(IllegalStateException.class,
                () -> IndividualRecipeSupport.register("mymod:assembler"));
    }

    @Test
    void freezingTwiceIsHarmless() {
        IndividualRecipeSupport.freeze();
        IndividualRecipeSupport.freeze();
        assertTrue(IndividualRecipeSupport.isFrozen());
    }

    @Test
    void aBlankIdIsRejectedRatherThanStored() {
        assertThrows(IllegalArgumentException.class, () -> IndividualRecipeSupport.register(null));
        assertThrows(IllegalArgumentException.class, () -> IndividualRecipeSupport.register(""));
        assertThrows(IllegalArgumentException.class, () -> IndividualRecipeSupport.register("  "));
    }

    @Test
    void theAuditAndThePickerReadTheSameRegistry() {
        // The whole point of the class. If these two ever diverge, the picker offers a lock the
        // audit then reports as impossible.
        IndividualRecipeSupport.register("mymod:assembler");
        assertTrue(IndividualRecipeSupport.supportedTypeIds().contains("mymod:assembler"));
        assertTrue(IndividualRecipeSupport.supports("mymod:assembler"));
    }
}
