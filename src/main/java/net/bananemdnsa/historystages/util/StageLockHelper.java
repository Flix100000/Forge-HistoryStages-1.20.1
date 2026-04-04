package net.bananemdnsa.historystages.util;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Combines global and individual stage lock checks into a single utility.
 * Use this instead of calling StageManager.isItemLocked() directly when
 * individual stage support is needed.
 */
public class StageLockHelper {

    // =============================================
    // SERVER-SIDE CHECKS (need player UUID)
    // =============================================

    /**
     * Checks if an item is locked for a specific player (global OR individual).
     * Server-side only.
     */
    public static boolean isItemLockedForPlayer(ItemStack stack, ServerPlayer player) {
        return isItemLockedForPlayer(stack, player.getUUID());
    }

    /**
     * Checks if an item is locked for a specific player UUID (global OR individual).
     * Server-side only.
     */
    public static boolean isItemLockedForPlayer(ItemStack stack, UUID playerUuid) {
        if (stack.isEmpty()) return false;
        ResourceLocation res = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (res == null) return false;

        String itemId = res.toString();
        String modId = res.getNamespace();

        // Check global stages first
        if (isGlobalItemLocked(itemId, modId)) return true;

        // Check individual stages
        if (isIndividualItemLocked(itemId, modId, playerUuid)) return true;

        return false;
    }

    /**
     * Checks if an item is locked ONLY by individual stages for a player.
     * Server-side only. Used for individual-specific behavior (pickup prevention, etc.)
     */
    public static boolean isItemLockedByIndividualStage(ItemStack stack, UUID playerUuid) {
        if (stack.isEmpty()) return false;
        ResourceLocation res = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (res == null) return false;

        return isIndividualItemLocked(res.toString(), res.getNamespace(), playerUuid);
    }

    private static boolean isGlobalItemLocked(String itemId, String modId) {
        List<String> requiredStages = StageManager.getAllStagesForItemOrMod(itemId, modId);
        for (String stage : requiredStages) {
            if (!StageData.SERVER_CACHE.contains(stage)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIndividualItemLocked(String itemId, String modId, UUID playerUuid) {
        List<String> requiredStages = StageManager.getAllIndividualStagesForItemOrMod(itemId, modId);
        if (requiredStages.isEmpty()) return false;

        Set<String> playerStages = IndividualStageData.SERVER_CACHE.getOrDefault(playerUuid, Collections.emptySet());
        for (String stage : requiredStages) {
            if (!playerStages.contains(stage)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a dimension is locked for a specific player (global OR individual).
     * Server-side only.
     */
    public static boolean isDimensionLockedForPlayer(String dimensionId, UUID playerUuid) {
        // Check global
        List<String> globalStages = StageManager.getAllStagesForDimension(dimensionId);
        for (String stage : globalStages) {
            if (!StageData.SERVER_CACHE.contains(stage)) {
                return true;
            }
        }

        // Check individual
        List<String> individualStages = StageManager.getAllIndividualStagesForDimension(dimensionId);
        if (!individualStages.isEmpty()) {
            Set<String> playerStages = IndividualStageData.SERVER_CACHE.getOrDefault(playerUuid, Collections.emptySet());
            for (String stage : individualStages) {
                if (!playerStages.contains(stage)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks if an entity is attack-locked for a specific player (global OR individual).
     * Server-side only.
     */
    public static boolean isEntityAttackLockedForPlayer(String entityId, UUID playerUuid) {
        // Check global (includes spawnlock entities which are also attack-locked)
        List<String> globalStages = StageManager.getAllStagesForAttackLockedEntity(entityId);
        for (String stage : globalStages) {
            if (!StageData.SERVER_CACHE.contains(stage)) {
                return true;
            }
        }

        // Check individual (attacklock only, no spawnlock)
        List<String> individualStages = StageManager.getAllIndividualStagesForAttackLockedEntity(entityId);
        if (!individualStages.isEmpty()) {
            Set<String> playerStages = IndividualStageData.SERVER_CACHE.getOrDefault(playerUuid, Collections.emptySet());
            for (String stage : individualStages) {
                if (!playerStages.contains(stage)) {
                    return true;
                }
            }
        }

        return false;
    }

    // =============================================
    // CLIENT-SIDE CHECKS (current player only)
    // =============================================

    /**
     * Checks if an item is locked on the client side (global OR individual).
     */
    public static boolean isItemLockedForClient(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation res = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (res == null) return false;

        String itemId = res.toString();
        String modId = res.getNamespace();

        // Check global stages
        List<String> globalStages = StageManager.getAllStagesForItemOrMod(itemId, modId);
        for (String stage : globalStages) {
            if (!ClientStageCache.isStageUnlocked(stage)) {
                return true;
            }
        }

        // Check individual stages
        List<String> individualStages = StageManager.getAllIndividualStagesForItemOrMod(itemId, modId);
        for (String stage : individualStages) {
            if (!ClientIndividualStageCache.isStageUnlocked(stage)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if an item is locked ONLY by individual stages on the client side.
     * Used for silver lock icon rendering.
     */
    public static boolean isItemLockedByIndividualStageClient(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation res = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (res == null) return false;

        List<String> individualStages = StageManager.getAllIndividualStagesForItemOrMod(res.toString(), res.getNamespace());
        for (String stage : individualStages) {
            if (!ClientIndividualStageCache.isStageUnlocked(stage)) {
                return true;
            }
        }
        return false;
    }

    // =============================================
    // ITEM DROP ON STAGE REVOCATION
    // =============================================

    /**
     * Drops all items from a player's inventory that are locked by the given individual stage.
     * Called when an individual stage is revoked from a player.
     */
    public static void dropLockedItemsForPlayer(ServerPlayer player, String revokedStageId) {
        StageEntry entry = StageManager.getIndividualStages().get(revokedStageId);
        if (entry == null) return;

        Inventory inv = player.getInventory();
        boolean dropped = false;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            ResourceLocation res = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (res == null) continue;

            String itemId = res.toString();
            String modId = res.getNamespace();

            // Check if this item is covered by the revoked stage
            if (!isItemInStage(itemId, modId, stack.getItem(), entry)) continue;

            // Check if this item is STILL locked for this player after the revocation
            // (another individual stage might still cover it)
            if (isIndividualItemLocked(itemId, modId, player.getUUID())) {
                player.drop(stack.copy(), false);
                inv.setItem(i, ItemStack.EMPTY);
                dropped = true;
            }
        }

        if (dropped) {
            player.containerMenu.broadcastChanges();
        }
    }

    private static boolean isItemInStage(String itemId, String modId, net.minecraft.world.item.Item item, StageEntry entry) {
        if (entry.getItems().contains(itemId)) return true;
        if (entry.getMods().contains(modId)) return true;

        if (item != null && entry.getTags() != null) {
            for (String tagId : entry.getTags()) {
                var tagKey = net.minecraft.tags.TagKey.create(
                        net.minecraft.core.registries.Registries.ITEM,
                        new ResourceLocation(tagId)
                );
                if (item.builtInRegistryHolder().is(tagKey)) return true;
            }
        }

        return false;
    }
}
