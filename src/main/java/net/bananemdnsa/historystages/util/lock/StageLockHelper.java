package net.bananemdnsa.historystages.util.lock;

import net.bananemdnsa.historystages.data.lock.category.BuiltInLockMatching;
import net.bananemdnsa.historystages.client.cache.ClientStageStates;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.NbtMatcher;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.lock.engine.LockResolution;
import net.bananemdnsa.historystages.data.lock.engine.CategoryLockIndexes;
import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Combines the global and individual halves of a lock check into one answer per subject.
 *
 * <p>Every method here asks the lock engine which stages gate the subject and resolves that
 * against the right viewer, so callers never touch the stage maps or the unlock caches
 * themselves. That is what keeps the engine swappable.
 */
public class StageLockHelper {

    // =============================================
    // SERVER-SIDE CHECKS (need player UUID)
    // =============================================

    public static boolean isItemLockedForPlayer(ItemStack stack, ServerPlayer player) {
        return isItemLockedForPlayer(stack, player.getUUID());
    }

    public static boolean isItemLockedForPlayer(ItemStack stack, UUID playerUuid) {
        ResourceLocation res = itemKey(stack);
        if (res == null) return false;
        String itemId = res.toString();
        String modId = res.getNamespace();

        // The yes-or-no form, so the engine can answer in bits instead of building two lists.
        // This is the call a per-frame consumer makes; the list form is for printing.
        return StageLocks.engine().isItemLocked(itemId, modId, stack, StageScope.GLOBAL,
                        StageLocks.serverGlobal(), CategoryLockIndexes.globalUnlocked())
                || StageLocks.engine().isItemLocked(itemId, modId, stack, StageScope.INDIVIDUAL,
                        StageLocks.serverIndividual(playerUuid),
                        CategoryLockIndexes.individualUnlocked(playerUuid));
    }

    public static boolean isItemLockedByIndividualStage(ItemStack stack, UUID playerUuid) {
        ResourceLocation res = itemKey(stack);
        if (res == null) return false;

        return StageLocks.engine().isItemLocked(res.toString(), res.getNamespace(), stack,
                StageScope.INDIVIDUAL, StageLocks.serverIndividual(playerUuid),
                CategoryLockIndexes.individualUnlocked(playerUuid));
    }

    /** Global-scope item check without a player, for paths that have no player context. */
    public static boolean isItemLockedForServer(ItemStack stack) {
        ResourceLocation res = itemKey(stack);
        if (res == null) return false;

        return StageLocks.engine().isItemLocked(res.toString(), res.getNamespace(), stack,
                StageScope.GLOBAL, StageLocks.serverGlobal(),
                CategoryLockIndexes.globalUnlocked());
    }

    /** Null for an empty stack or an unregistered item — every item check starts here. */
    @Nullable
    private static ResourceLocation itemKey(ItemStack stack) {
        if (stack.isEmpty()) return null;
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    // =============================================
    // ACTION-SPECIFIC SERVER-SIDE CHECKS
    // =============================================

    /**
     * Checks if a specific action is locked for an item in any locked global stage.
     * Server-side only.
     */
    public static boolean isActionLockedForPlayer(ItemStack stack, UUID playerUuid, String action) {
        if (stack.isEmpty()) return false;
        return StageLocks.engine().isItemActionLocked(stack, action, StageScope.GLOBAL,
                StageLocks.serverGlobal());
    }

    /**
     * Global-scope action check without a player, for loot and recipe paths where no player
     * context exists.
     */
    public static boolean isActionLockedForServer(ItemStack stack, String action) {
        if (stack.isEmpty()) return false;
        return StageLocks.engine().isItemActionLocked(stack, action, StageScope.GLOBAL,
                StageLocks.serverGlobal());
    }

    /**
     * Checks if a specific action is locked for an item in any locked individual stage for this player.
     * Server-side only.
     */
    public static boolean isActionLockedByIndividualStage(ItemStack stack, UUID playerUuid, String action) {
        if (stack.isEmpty()) return false;
        return StageLocks.engine().isItemActionLocked(stack, action, StageScope.INDIVIDUAL,
                StageLocks.serverIndividual(playerUuid));
    }

    // =============================================
    // ACTION-SPECIFIC CLIENT-SIDE CHECKS
    // =============================================

    /**
     * Checks if a specific action is locked for an item in any locked global stage.
     * Client-side only.
     */
    public static boolean isActionLockedForClient(ItemStack stack, String action) {
        if (stack.isEmpty()) return false;
        return StageLocks.engine().isItemActionLocked(stack, action, StageScope.GLOBAL,
                ClientStageStates.global());
    }

    /**
     * Checks if a specific action is locked for an item in any locked individual stage.
     * Client-side only.
     */
    public static boolean isActionLockedByIndividualStageClient(ItemStack stack, String action) {
        if (stack.isEmpty()) return false;
        return StageLocks.engine().isItemActionLocked(stack, action, StageScope.INDIVIDUAL,
                ClientStageStates.individual());
    }

    /**
     * Checks if a dimension is locked for a specific player (global OR individual).
     * Server-side only.
     */
    public static boolean isDimensionLockedForPlayer(String dimensionId, UUID playerUuid) {
        return LockResolution.isLocked(
                StageLocks.engine().gatingStagesForDimension(dimensionId, StageScope.GLOBAL),
                StageLocks.serverGlobal(),
                StageLocks.engine().gatingStagesForDimension(dimensionId, StageScope.INDIVIDUAL),
                StageLocks.serverIndividual(playerUuid));
    }

    public static boolean isEntityAttackLockedForPlayer(String entityId, UUID playerUuid) {
        return LockResolution.isLocked(
                StageLocks.engine().gatingStagesForEntityAttack(entityId, StageScope.GLOBAL),
                StageLocks.serverGlobal(),
                StageLocks.engine().gatingStagesForEntityAttack(entityId, StageScope.INDIVIDUAL),
                StageLocks.serverIndividual(playerUuid));
    }

    // =============================================
    // CLIENT-SIDE CHECKS (current player only)
    // =============================================

    public static boolean isItemLockedForClient(ItemStack stack) {
        ResourceLocation res = itemKey(stack);
        if (res == null) return false;
        String itemId = res.toString();
        String modId = res.getNamespace();

        return LockResolution.isLocked(
                StageLocks.engine().gatingStagesForItem(itemId, modId, stack, StageScope.GLOBAL),
                ClientStageStates.global(),
                StageLocks.engine().gatingStagesForItem(itemId, modId, stack, StageScope.INDIVIDUAL),
                ClientStageStates.individual());
    }

    /**
     * Lenient client check: an item counts as locked only when it is gated at all and none of
     * its gating stages is unlocked. Used by JEI/EMI hiding when
     * {@code Config.VISUAL.lockedItemMultiStagePolicy == LENIENT}.
     */
    public static boolean isItemLockedForClientLenient(ItemStack stack) {
        ResourceLocation res = itemKey(stack);
        if (res == null) return false;
        String itemId = res.toString();
        String modId = res.getNamespace();

        return LockResolution.isLockedLenient(
                StageLocks.engine().gatingStagesForItem(itemId, modId, stack, StageScope.GLOBAL),
                ClientStageStates.global(),
                StageLocks.engine().gatingStagesForItem(itemId, modId, stack, StageScope.INDIVIDUAL),
                ClientStageStates.individual());
    }

    public static boolean isItemLockedByIndividualStageClient(ItemStack stack) {
        ResourceLocation res = itemKey(stack);
        if (res == null) return false;

        return LockResolution.isLocked(
                StageLocks.engine().gatingStagesForItem(res.toString(), res.getNamespace(),
                        stack, StageScope.INDIVIDUAL),
                ClientStageStates.individual());
    }

    /**
     * Returns true when an item is in the global phase of a dual-phase lock.
     * Dual-phase: the item appears in both a global and an individual stage config.
     * Returns true when at least one of the paired global stages is not yet unlocked client-side.
     */
    public static boolean isDualPhaseGloballyLockedClient(ItemStack stack) {
        ResourceLocation res = itemKey(stack);
        if (res == null) return false;

        return LockResolution.isLocked(
                StageLocks.engine().globalDualPhaseStagesForItem(
                        res.toString(), res.getNamespace(), stack.getItem()),
                ClientStageStates.global());
    }

    // =============================================
    // RECIPE LOCK CHECKS
    // =============================================

    /** Global-scope recipe check against the server's unlocked set. */
    public static boolean isRecipeLockedForServer(String recipeId) {
        return LockResolution.isLocked(
                StageLocks.engine().gatingStagesForRecipe(recipeId, StageScope.GLOBAL),
                StageLocks.serverGlobal());
    }

    /**
     * Global-scope-only recipe check on the client. Kept separate from
     * {@link #isRecipeLockedForClient} (both scopes) because {@code RecipeManagerMixin} feeds
     * this into live recipe resolution (crafting-grid output prediction, recipe book), where
     * consulting individual stages would newly filter recipes that were never gated there
     * before — a real verdict change, not just a tidiness one. Matches the legacy behavior of
     * {@code RecipeHandler.isRecipeIdLocked}'s client branch exactly.
     */
    public static boolean isRecipeLockedForClientGlobalOnly(String recipeId) {
        return LockResolution.isLocked(
                StageLocks.engine().gatingStagesForRecipe(recipeId, StageScope.GLOBAL),
                ClientStageStates.global());
    }

    /** Client-side recipe check across both scopes — what JEI, EMI and the mixin ask. */
    public static boolean isRecipeLockedForClient(String recipeId) {
        return LockResolution.isLocked(
                StageLocks.engine().gatingStagesForRecipe(recipeId, StageScope.GLOBAL),
                ClientStageStates.global(),
                StageLocks.engine().gatingStagesForRecipe(recipeId, StageScope.INDIVIDUAL),
                ClientStageStates.individual());
    }

    // =============================================
    // ITEM DROP ON STAGE REVOCATION
    // =============================================

    public static void dropLockedItemsForPlayer(ServerPlayer player, String revokedStageId) {
        StageEntry entry = StageManager.getIndividualStages().get(revokedStageId);
        if (entry == null) return;

        Inventory inv = player.getInventory();
        boolean dropped = false;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            ResourceLocation res = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (res == null) continue;

            String itemId = res.toString();
            String modId = res.getNamespace();

            if (!isItemInStage(itemId, modId, stack, entry)) continue;

            if (isItemLockedByIndividualStage(stack, player.getUUID())) {
                player.drop(stack.copy(), false);
                inv.setItem(i, ItemStack.EMPTY);
                dropped = true;
            }
        }

        if (dropped) {
            player.containerMenu.broadcastChanges();
        }
    }

    // =============================================
    // ENCHANTMENT LOCK CHECKS
    // =============================================

    public static boolean isEnchantmentLockedForPlayer(String enchantmentId, int level, UUID playerUuid) {
        return LockResolution.isLocked(
                StageLocks.engine().gatingStagesForEnchantment(enchantmentId, level, StageScope.GLOBAL),
                StageLocks.serverGlobal(),
                StageLocks.engine().gatingStagesForEnchantment(enchantmentId, level, StageScope.INDIVIDUAL),
                StageLocks.serverIndividual(playerUuid));
    }

    private static boolean isItemInStage(String itemId, String modId, ItemStack stack, StageEntry entry) {
        for (ItemEntry itemEntry : entry.getItemEntries()) {
            if (itemEntry.getId().equals(itemId)) {
                if (itemEntry.hasNbt()) {
                    if (NbtMatcher.matches(stack, itemEntry.getNbt())) return true;
                } else {
                    return true;
                }
            }
        }
        if (entry.getMods().contains(modId) && !entry.isModExcepted(itemId, stack)) return true;

        net.minecraft.world.item.Item item = stack.getItem();
        if (item != null) {
            for (net.bananemdnsa.historystages.data.lock.NamedLockEntry tagEntry : entry.getTagEntries()) {
                if (BuiltInLockMatching.tagEntryMatches(tagEntry, stack, item)) return true;
            }
        }

        return false;
    }
}
