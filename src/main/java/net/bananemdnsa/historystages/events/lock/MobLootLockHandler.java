package net.bananemdnsa.historystages.events.lock;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MobLootLockHandler {
    private static final Random RANDOM = new Random();

    @SubscribeEvent
    public static void onMobDrops(LivingDropsEvent event) {
        // Falls Mob-Loot-Sperre in der Config deaktiviert ist, direkt abbrechen
        if (!Config.COMMON.lockMobLoot.get()) return;

        // Nur auf dem Server arbeiten
        if (event.getEntity().level().isClientSide()) return;

        // Sicherstellen, dass der Cache geladen ist
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

        // Bereinigung: Alle Einträge entfernen, die jetzt AIR sind
        drops.removeIf(itemEntity -> itemEntity.getItem().isEmpty() || itemEntity.getItem().is(Items.AIR));

        if (replacedCount > 0) {
            ResourceLocation entityType = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
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
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(randomId));
            if (item != null && item != Items.AIR) {
                return new ItemStack(item, count);
            }
        } catch (Exception ignored) {}

        return new ItemStack(Items.COBBLESTONE, count);
    }
}