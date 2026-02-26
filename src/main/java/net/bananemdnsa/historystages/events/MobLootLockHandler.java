package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
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

        Collection<ItemEntity> drops = event.getDrops();

        for (ItemEntity itemEntity : drops) {
            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty()) continue;

            ResourceLocation resLoc = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (resLoc == null) continue;

            String requiredStage = StageManager.getStageForItemOrMod(resLoc.toString(), resLoc.getNamespace());

            // Wenn das Item einer gesperrten Stage angehört
            if (requiredStage != null && !StageData.SERVER_CACHE.contains(requiredStage)) {

                if (Config.COMMON.useReplacements.get()) {
                    // Item durch Ersatz aus der Config tauschen
                    itemEntity.setItem(getReplacement(stack.getCount()));
                } else {
                    // Item komplett entfernen (auf AIR setzen)
                    itemEntity.setItem(new ItemStack(Items.AIR));
                }
            }
        }

        // Bereinigung: Alle Einträge entfernen, die jetzt AIR sind
        drops.removeIf(itemEntity -> itemEntity.getItem().isEmpty() || itemEntity.getItem().is(Items.AIR));
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