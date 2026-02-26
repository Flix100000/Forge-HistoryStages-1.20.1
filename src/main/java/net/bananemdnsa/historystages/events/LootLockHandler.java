package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Random;

@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LootLockHandler {
    private static final Random RANDOM = new Random();

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        // Nur auf dem Server ausführen
        if (event.getEntity().level().isClientSide()) return;

        // Sicherstellen, dass der Stage-Cache geladen ist
        if (StageData.SERVER_CACHE.isEmpty()) {
            StageData.get(event.getEntity().level());
        }

        AbstractContainerMenu menu = event.getContainer();
        boolean changed = false;

        // Wir loopen durch alle Slots im geöffneten Menü (Kiste + Spieler-Inventar)
        for (Slot slot : menu.slots) {

            // WICHTIG: Wir filtern nur die Slots, die NICHT zum Inventar des Spielers gehören.
            // So verhindern wir, dass Items, die der Spieler bereits besitzt, gelöscht werden.
            if (slot.container == event.getEntity().getInventory()) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            ResourceLocation resLoc = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (resLoc == null) continue;

            String itemId = resLoc.toString();
            String modId = resLoc.getNamespace();

            // Prüfen, ob das Item eine Stage benötigt
            String requiredStage = StageManager.getStageForItemOrMod(itemId, modId);

            // Wenn die Stage gesperrt ist (nicht im SERVER_CACHE)
            if (requiredStage != null && !StageData.SERVER_CACHE.contains(requiredStage)) {
                if (Config.COMMON.useReplacements.get()) {
                    slot.set(getReplacement(stack.getCount()));
                } else {
                    slot.set(ItemStack.EMPTY);
                }
                changed = true;
            }
        }

        // Falls wir etwas geändert haben, schicken wir ein Update an den Client
        if (changed) {
            menu.broadcastChanges();
        }
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