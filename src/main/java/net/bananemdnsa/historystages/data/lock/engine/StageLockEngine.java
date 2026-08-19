package net.bananemdnsa.historystages.data.lock.engine;

import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The "which stages gate this subject" half of a lock question — the seam every lock check
 * in the mod goes through.
 *
 * <p>Implementations answer the mapping only; they never look at what a player has unlocked.
 * Callers pair the answer with a {@link StageStateView} through {@link LockResolution}. That
 * split is what lets a later phase replace the whole implementation with bitmask lookups
 * without touching a caller, and it keeps client-only caches out of server-reachable code.
 *
 * <p>Every method defaults to "nothing gates this", so an implementation can cover subjects
 * incrementally.
 */
public interface StageLockEngine {

    default List<String> gatingStagesForItem(String itemId, String modId,
                                             @Nullable ItemStack stack, StageScope scope) {
        return List.of();
    }

    /**
     * Dual-phase means the same item is gated by a global stage <em>and</em> an individual one.
     * Only the global half is asked for here — the client uses it to tell "not yours yet" apart
     * from "not anyone's yet". Global scope only, which is why there is no scope parameter.
     */
    default List<String> globalDualPhaseStagesForItem(String itemId, String modId, @Nullable Item item) {
        return List.of();
    }

    /**
     * Whether a specific action is blocked for this stack. Takes the state view directly because
     * the implementation walks a candidate index and skips already-unlocked stages — turning that
     * into a stage list first would throw away the short-circuit.
     */
    default boolean isItemActionLocked(ItemStack stack, String action, StageScope scope, StageStateView state) {
        return false;
    }

    default List<String> gatingStagesForRecipe(String recipeId, StageScope scope) {
        return List.of();
    }

    default List<String> gatingStagesForDimension(String dimensionId, StageScope scope) {
        return List.of();
    }

    default List<String> gatingStagesForStructure(String structureId, StageScope scope) {
        return List.of();
    }

    default List<String> gatingStagesForEntityAttack(String entityId, StageScope scope) {
        return List.of();
    }

    default List<String> gatingStagesForEntityInteraction(String entityId, String action,
                                                          ItemStack held, StageScope scope) {
        return List.of();
    }

    /**
     * Spawn locks are global-only today — there is no individual counterpart in the data model.
     * The scope parameter exists so the signature does not have to change when that gap is
     * closed; {@link StringStageLockEngine} returns an empty list for
     * {@link StageScope#INDIVIDUAL}, which is exactly what the old code did.
     */
    default List<String> gatingStagesForEntitySpawn(String entityId, String source,
                                                    String dimension, StageScope scope) {
        return List.of();
    }

    /** Stages with a spawnlock entry for this entity in this dimension, regardless of source. */
    default List<String> gatingStagesWithSpawnEntry(String entityId, String dimension, StageScope scope) {
        return List.of();
    }

    default List<String> gatingStagesForEnchantment(String enchantmentId, int level, StageScope scope) {
        return List.of();
    }

    /** Fast-out for the per-tick structure handler: is any structure gated at all? */
    default boolean anyStructureLocks() {
        return false;
    }

    /** Fast-out for the per-tick biome handler: is any biome gated at all? */
    default boolean anyBiomeLocks() {
        return false;
    }
}
