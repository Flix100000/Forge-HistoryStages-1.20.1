package net.bananemdnsa.historystages.events.lock;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public class MobLootLockHandler {
    private static final Random RANDOM = new Random();

    @SubscribeEvent
    public static void onMobDrops(LivingDropsEvent event) {
        if (!Config.COMMON.lockMobLoot.get()) return;
        if (event.getEntity().level().isClientSide()) return;

        if (StageData.SERVER_CACHE.isEmpty()) {
            StageData.get(event.getEntity().level());
        }

        // Mob drops are a single set of world items shared by everyone, so there is no per-player
        // view to strip. The killing player is the closest stand-in: their individual stages decide.
        // Without a player killer (fall damage, mob-on-mob, …) only global stages apply.
        UUID killerUuid = event.getSource().getEntity() instanceof Player player ? player.getUUID() : null;

        Collection<ItemEntity> drops = event.getDrops();
        int replacedCount = 0;

        for (ItemEntity itemEntity : drops) {
            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty()) continue;

            if (isLootLocked(stack, killerUuid)) {
                if (Config.COMMON.useReplacements.get()) {
                    itemEntity.setItem(getReplacement(stack.getCount()));
                } else {
                    itemEntity.setItem(new ItemStack(Items.AIR));
                }
                replacedCount++;
            }
        }

        drops.removeIf(itemEntity -> itemEntity.getItem().isEmpty() || itemEntity.getItem().is(Items.AIR));

        if (replacedCount > 0) {
            ResourceLocation entityType = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
            DebugLogger.runtimeThrottled("Mob Loot Lock", "mobloot_" + entityType,
                    "Replaced " + replacedCount + " locked drop(s) from '" + entityType + "' [action: loot]");
        }
    }

    private static boolean isLootLocked(ItemStack stack, UUID killerUuid) {
        if (StageLockHelper.isActionLockedForServer(stack, "loot")) return true;
        return killerUuid != null
                && Config.COMMON.individualLockLoot.get()
                && StageLockHelper.isActionLockedByIndividualStage(stack, killerUuid, "loot");
    }

    private static ItemStack getReplacement(int count) {
        List<? extends String> list = Config.COMMON.replacementItems.get();
        if (list == null || list.isEmpty()) return new ItemStack(Items.COBBLESTONE, count);

        try {
            String randomId = list.get(RANDOM.nextInt(list.size()));
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(randomId));
            if (item != null && item != Items.AIR) {
                return new ItemStack(item, count);
            }
        } catch (Exception ignored) {}

        return new ItemStack(Items.COBBLESTONE, count);
    }
}
