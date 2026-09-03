package net.bananemdnsa.historystages.data.lock.engine;

import net.bananemdnsa.historystages.api.stage.StageStateView;

import net.bananemdnsa.historystages.api.stage.StageScope;
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
     * The same question narrowed to one action: which stages block <em>this</em> action on the
     * item.
     *
     * <p>{@link #isItemActionLocked} answers the same thing faster, but only as a yes or no
     * against one viewer. A caller that has to apply its own resolution policy over the gating
     * stages — recipe-viewer hiding does, it treats "some stage is unlocked" differently from
     * "none is" — needs them named. The default ignores the action, which is what an engine that
     * does not model actions should answer.
     */
    default List<String> gatingStagesForItemAction(String itemId, String modId,
                                                   @Nullable ItemStack stack, String action,
                                                   StageScope scope) {
        return gatingStagesForItem(itemId, modId, stack, scope);
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

    /**
     * Whether a specific action is blocked for a bare fluid id, with no stack behind it.
     *
     * <p>The counterpart to {@link #isItemActionLocked} for the one fluid question no container
     * can answer: an <em>empty</em> bucket held against a pool carries nothing, so the subject
     * built from the stack says nothing about the fluid the player is reaching for. That fluid
     * comes from the block, and this is how it gets asked about.
     */
    default boolean isFluidActionLocked(String fluidId, String action, StageScope scope,
                                        StageStateView state) {
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

    /**
     * Fast-out for the entity-spawn handler: is any entity spawn gated at all?
     *
     * <p>That handler runs on EntityJoinLevel, which fires for every arrow, item, XP orb and
     * falling block in the world - so the question has to be answerable before anything is built
     * to ask it with.
     */
    default boolean anyEntitySpawnLocks() {
        return false;
    }

    /** Fast-out for the per-tick biome handler: is any biome gated at all? */
    default boolean anyBiomeLocks() {
        return false;
    }

    /**
     * The stages changed, so anything derived from them is stale.
     *
     * <p>Raised by the stage store after every write to its maps. This is deliberately not a lock
     * question — it asks nothing, it announces — so it does not weaken the seam, and it is the
     * lifecycle point an engine hangs its compile step on: today an index rebuild, later a
     * bitmask bake.
     */
    default void stagesChanged() {}

    /**
     * Whether this item is locked for this viewer — the yes-or-no form of
     * {@link #gatingStagesForItem}.
     *
     * <p>Separate because it is a different question, not a convenience. The list form has to
     * name the stages, which means producing them; this one only has to decide, which an engine
     * can do without ever building a list. {@code unlocked} is the viewer's state as bits where
     * the caller has it, and null where it does not — an implementation must answer correctly
     * from {@code state} either way.
     */
    default boolean isItemLocked(String itemId, String modId, @Nullable ItemStack stack,
                                 StageScope scope, StageStateView state, @Nullable StageMask unlocked) {
        return LockResolution.isLocked(gatingStagesForItem(itemId, modId, stack, scope), state);
    }
}
