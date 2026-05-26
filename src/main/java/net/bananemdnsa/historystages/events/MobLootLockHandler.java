package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.StageData;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Random;

public final class MobLootLockHandler {
    private static final Random RANDOM = new Random();

    private MobLootLockHandler() {
    }

    /**
     * Called from {@link net.bananemdnsa.historystages.mixin.LivingEntityDropMixin}
     * for every stack that {@code LivingEntity.dropFromLootTable} would spawn.
     * Returns the stack to spawn (possibly replaced), or {@link ItemStack#EMPTY}
     * if the drop should be suppressed entirely.
     */
    public static ItemStack filterDrop(LivingEntity entity, ItemStack stack) {
        if (stack.isEmpty()) return stack;
        if (!Config.COMMON.lockMobLoot) return stack;
        if (entity.level().isClientSide()) return stack;

        if (StageData.SERVER_CACHE.isEmpty()) {
            StageData.get(entity.level());
        }

        if (!StageLockHelper.isActionLockedForServer(stack, "loot")) {
            return stack;
        }

        ResourceLocation entityType = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        DebugLogger.runtimeThrottled("Mob Loot Lock", "mobloot_" + entityType,
                "Replaced locked drop from '" + entityType + "' [action: loot]");

        if (Config.COMMON.useReplacements) {
            return getReplacement(stack.getCount());
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack getReplacement(int count) {
        List<String> list = Config.COMMON.replacementItems;
        if (list == null || list.isEmpty()) {
            return new ItemStack(Items.COBBLESTONE, count);
        }
        try {
            String randomId = list.get(RANDOM.nextInt(list.size()));
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(randomId));
            if (item != null && item != Items.AIR) {
                return new ItemStack(item, count);
            }
        } catch (Exception ignored) {
        }
        return new ItemStack(Items.COBBLESTONE, count);
    }
}
