package net.bananemdnsa.historystages.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class StageLockHelper {
    private StageLockHelper() {
    }

    public static boolean isItemLockedForPlayer(ItemStack stack, ServerPlayer player) {
        return isItemLockedForPlayer(stack, player.getUUID());
    }

    public static boolean isItemLockedForPlayer(ItemStack stack, UUID playerUuid) {
        if (stack.isEmpty()) {
            return false;
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return false;
        }

        return isGlobalItemLocked(id.toString(), id.getNamespace(), stack)
                || isIndividualItemLocked(id.toString(), id.getNamespace(), stack, playerUuid);
    }

    public static boolean isItemLockedByIndividualStage(ItemStack stack, UUID playerUuid) {
        if (stack.isEmpty()) {
            return false;
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return false;
        }

        return isIndividualItemLocked(id.toString(), id.getNamespace(), stack, playerUuid);
    }

    public static boolean isDimensionLockedForPlayer(String dimensionId, UUID playerUuid) {
        for (String stage : StageManager.getAllStagesForDimension(dimensionId)) {
            if (!StageData.SERVER_CACHE.contains(stage)) {
                return true;
            }
        }

        Set<String> individualStages = IndividualStageData.SERVER_CACHE.getOrDefault(playerUuid, Collections.emptySet());
        for (String stage : StageManager.getAllIndividualStagesForDimension(dimensionId)) {
            if (!individualStages.contains(stage)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isEntityAttackLockedForPlayer(String entityId, UUID playerUuid) {
        for (String stage : StageManager.getAllStagesForAttackLockedEntity(entityId)) {
            if (!StageData.SERVER_CACHE.contains(stage)) {
                return true;
            }
        }

        Set<String> individualStages = IndividualStageData.SERVER_CACHE.getOrDefault(playerUuid, Collections.emptySet());
        for (String stage : StageManager.getAllIndividualStagesForAttackLockedEntity(entityId)) {
            if (!individualStages.contains(stage)) {
                return true;
            }
        }

        return false;
    }

    public static List<String> getMissingStageDisplayNamesForEntityAttack(String entityId, UUID playerUuid) {
        List<String> names = new java.util.ArrayList<>();
        for (String stage : StageManager.getAllStagesForAttackLockedEntity(entityId)) {
            if (!StageData.SERVER_CACHE.contains(stage)) {
                StageEntry entry = StageManager.getStages().get(stage);
                names.add(entry != null ? entry.getDisplayName() : stage);
            }
        }
        Set<String> individualStages = IndividualStageData.SERVER_CACHE.getOrDefault(playerUuid, Collections.emptySet());
        for (String stage : StageManager.getAllIndividualStagesForAttackLockedEntity(entityId)) {
            if (!individualStages.contains(stage)) {
                StageEntry entry = StageManager.getIndividualStages().get(stage);
                names.add(entry != null ? entry.getDisplayName() : stage);
            }
        }
        return names;
    }

    public static boolean isDualPhaseGloballyLockedClient(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation res = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (res == null) return false;
        String itemId = res.toString();
        String modId = res.getNamespace();

        Set<String> itemStages = StageManager.getDualPhaseItems().get(itemId);
        if (itemStages != null) {
            for (String stage : itemStages) {
                if (!ClientStageCache.isStageUnlocked(stage)) return true;
            }
        }
        Set<String> modStages = StageManager.getDualPhaseMods().get(modId);
        if (modStages != null) {
            for (String stage : modStages) {
                if (!ClientStageCache.isStageUnlocked(stage)) return true;
            }
        }
        Item item = stack.getItem();
        for (Map.Entry<String, Set<String>> tagEntry : StageManager.getDualPhaseTags().entrySet()) {
            net.minecraft.tags.TagKey<Item> tagKey = net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.ITEM,
                    ResourceLocation.parse(tagEntry.getKey()));
            if (item.builtInRegistryHolder().is(tagKey)) {
                for (String stage : tagEntry.getValue()) {
                    if (!ClientStageCache.isStageUnlocked(stage)) return true;
                }
            }
        }
        return false;
    }

    public static boolean isItemLockedForClient(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return false;
        }

        for (String stage : StageManager.getAllStagesForItemOrMod(id.toString(), id.getNamespace(), stack)) {
            if (!ClientStageCache.isStageUnlocked(stage)) {
                return true;
            }
        }

        for (String stage : StageManager.getAllIndividualStagesForItemOrMod(id.toString(), id.getNamespace(), stack)) {
            if (!ClientIndividualStageCache.isStageUnlocked(stage)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isItemLockedByIndividualStageClient(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return false;
        }

        for (String stage : StageManager.getAllIndividualStagesForItemOrMod(id.toString(), id.getNamespace(), stack)) {
            if (!ClientIndividualStageCache.isStageUnlocked(stage)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isActionLockedForPlayer(ItemStack stack, UUID playerUuid, String action) {
        if (stack.isEmpty()) {
            return false;
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return false;
        }

        for (Map.Entry<String, StageEntry> entry : StageManager.getStages().entrySet()) {
            if (StageData.SERVER_CACHE.contains(entry.getKey())) {
                continue;
            }
            if (StageManager.isItemActionLockedForStage(id.toString(), id.getNamespace(), stack, action, entry.getValue())) {
                return true;
            }
        }

        Set<String> individualStages = IndividualStageData.SERVER_CACHE.getOrDefault(playerUuid, Collections.emptySet());
        for (Map.Entry<String, StageEntry> entry : StageManager.getIndividualStages().entrySet()) {
            if (individualStages.contains(entry.getKey())) {
                continue;
            }
            if (StageManager.isItemActionLockedForStage(id.toString(), id.getNamespace(), stack, action, entry.getValue())) {
                return true;
            }
        }

        return false;
    }

    public static boolean isActionLockedForServer(ItemStack stack, String action) {
        if (stack.isEmpty()) {
            return false;
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return false;
        }

        for (Map.Entry<String, StageEntry> entry : StageManager.getStages().entrySet()) {
            if (!StageData.SERVER_CACHE.contains(entry.getKey())
                    && StageManager.isItemActionLockedForStage(id.toString(), id.getNamespace(), stack, action, entry.getValue())) {
                return true;
            }
        }

        return false;
    }

    public static boolean isActionLockedByIndividualStage(ItemStack stack, UUID playerUuid, String action) {
        if (stack.isEmpty()) {
            return false;
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return false;
        }

        Set<String> individualStages = IndividualStageData.SERVER_CACHE.getOrDefault(playerUuid, Collections.emptySet());
        for (Map.Entry<String, StageEntry> entry : StageManager.getIndividualStages().entrySet()) {
            if (!individualStages.contains(entry.getKey())
                    && StageManager.isItemActionLockedForStage(id.toString(), id.getNamespace(), stack, action, entry.getValue())) {
                return true;
            }
        }

        return false;
    }

    public static boolean isActionLockedForClient(ItemStack stack, String action) {
        if (stack.isEmpty()) {
            return false;
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return false;
        }

        for (Map.Entry<String, StageEntry> entry : StageManager.getStages().entrySet()) {
            if (!ClientStageCache.isStageUnlocked(entry.getKey())
                    && StageManager.isItemActionLockedForStage(id.toString(), id.getNamespace(), stack, action, entry.getValue())) {
                return true;
            }
        }

        return false;
    }

    public static boolean isActionLockedByIndividualStageClient(ItemStack stack, String action) {
        if (stack.isEmpty()) {
            return false;
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return false;
        }

        for (Map.Entry<String, StageEntry> entry : StageManager.getIndividualStages().entrySet()) {
            if (!ClientIndividualStageCache.isStageUnlocked(entry.getKey())
                    && StageManager.isItemActionLockedForStage(id.toString(), id.getNamespace(), stack, action, entry.getValue())) {
                return true;
            }
        }

        return false;
    }

    public static void dropLockedItemsForPlayer(ServerPlayer player, String revokedStageId) {
        StageEntry entry = StageManager.getIndividualStages().get(revokedStageId);
        if (entry == null) {
            return;
        }

        Inventory inventory = player.getInventory();
        boolean dropped = false;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }

            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null || !isItemInStage(id.toString(), id.getNamespace(), stack, entry)) {
                continue;
            }

            if (isItemLockedByIndividualStage(stack, player.getUUID())) {
                player.drop(stack.copy(), false);
                inventory.setItem(slot, ItemStack.EMPTY);
                dropped = true;
            }
        }

        if (dropped) {
            player.containerMenu.broadcastChanges();
        }
    }

    public static boolean isEnchantmentLockedForPlayer(String enchantmentId, int level, UUID playerUuid) {
        for (var entry : StageManager.getStages().entrySet()) {
            if (!StageData.SERVER_CACHE.contains(entry.getKey())
                    && stageLocksEnchantment(entry.getValue(), enchantmentId, level)) {
                return true;
            }
        }

        Set<String> playerStages = IndividualStageData.SERVER_CACHE.getOrDefault(playerUuid, Collections.emptySet());
        for (var entry : StageManager.getIndividualStages().entrySet()) {
            if (!playerStages.contains(entry.getKey())
                    && stageLocksEnchantment(entry.getValue(), enchantmentId, level)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isGlobalItemLocked(String itemId, String modId, ItemStack stack) {
        List<String> requiredStages = StageManager.getAllStagesForItemOrMod(itemId, modId, stack);
        for (String stage : requiredStages) {
            if (!StageData.SERVER_CACHE.contains(stage)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIndividualItemLocked(String itemId, String modId, ItemStack stack, UUID playerUuid) {
        List<String> requiredStages = StageManager.getAllIndividualStagesForItemOrMod(itemId, modId, stack);
        Set<String> playerStages = IndividualStageData.SERVER_CACHE.getOrDefault(playerUuid, Collections.emptySet());
        for (String stage : requiredStages) {
            if (!playerStages.contains(stage)) {
                return true;
            }
        }
        return false;
    }

    private static boolean stageLocksEnchantment(StageEntry stage, String enchantmentId, int level) {
        for (ItemEntry itemEntry : stage.getItemEntries()) {
            if (!itemEntry.hasNbt() || !"minecraft:enchanted_book".equals(itemEntry.getId())) {
                continue;
            }

            JsonObject nbt = itemEntry.getNbt();
            if (!nbt.has("StoredEnchantments") || !nbt.get("StoredEnchantments").isJsonArray()) {
                continue;
            }

            JsonArray enchantments = nbt.getAsJsonArray("StoredEnchantments");
            for (JsonElement element : enchantments) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject enchantment = element.getAsJsonObject();
                if (!enchantment.has("id") || !enchantmentId.equals(enchantment.get("id").getAsString())) {
                    continue;
                }
                if (!enchantment.has("lvl")) {
                    return true;
                }

                JsonElement levelElement = enchantment.get("lvl");
                if (levelElement.isJsonPrimitive() && levelElement.getAsJsonPrimitive().isNumber()) {
                    if (levelElement.getAsInt() == level) {
                        return true;
                    }
                } else if (levelElement.isJsonPrimitive() && levelElement.getAsJsonPrimitive().isString()) {
                    String range = levelElement.getAsString();
                    if (range.matches("\\d+-\\d+")) {
                        String[] parts = range.split("-");
                        int min = Integer.parseInt(parts[0]);
                        int max = Integer.parseInt(parts[1]);
                        if (level >= min && level <= max) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private static boolean isItemInStage(String itemId, String modId, ItemStack stack, StageEntry entry) {
        for (ItemEntry itemEntry : entry.getItemEntries()) {
            if (itemEntry.getId().equals(itemId)) {
                return !itemEntry.hasNbt() || net.bananemdnsa.historystages.data.NbtMatcher.matches(stack, itemEntry.getNbt());
            }
        }

        if (entry.getMods().contains(modId) && !entry.isModExcepted(itemId, stack)) {
            return true;
        }

        Item item = stack.getItem();
        for (String tagId : entry.getTags()) {
            ResourceLocation tagLocation = ResourceLocation.tryParse(tagId);
            if (tagLocation != null && item.builtInRegistryHolder().is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, tagLocation))) {
                return true;
            }
        }

        return false;
    }
}
