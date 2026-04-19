package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Mod.EventBusSubscriber(modid = "historystages", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LootLockHandler {
    private static final Random RANDOM = new Random();

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity().level().isClientSide()) return;

        if (event.getContainer().slots.isEmpty()) return;
        Container container = event.getContainer().slots.get(0).container;

        if (container == null) return;

        // Prüfen, ob es ein Lootr-Container ist (Klassennamen-Check)
        if (!container.getClass().getName().toLowerCase().contains("lootr")) return;

        boolean changed = false;
        int replacedCount = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;

            if (StageManager.isItemLockedForServer(stack)) {
                if (Config.COMMON.useReplacements.get()) {
                    container.setItem(i, getReplacement(stack.getCount()));
                } else {
                    container.setItem(i, ItemStack.EMPTY);
                }
                replacedCount++;
                changed = true;
            }
        }

        if (changed) {
            DebugLogger.runtime("Loot Lock", event.getEntity().getName().getString(),
                    "Replaced " + replacedCount + " locked item(s) in Lootr container");
            event.getContainer().broadcastChanges();
        }
    }

    private static ItemStack getReplacement(int count) {
        // 1. Priorität: Zufälliges Item aus der replacementItems Liste
        List<? extends String> list = Config.COMMON.replacementItems.get();
        if (list != null && !list.isEmpty()) {
            try {
                String randomId = list.get(RANDOM.nextInt(list.size()));
                Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(randomId));
                if (item != null && item != Items.AIR) {
                    return new ItemStack(item, count);
                }
            } catch (Exception ignored) {}
        }

        // 2. Priorität: Zufälliges Item aus den replacementTags
        List<? extends String> tags = Config.COMMON.replacementTag.get();
        if (tags != null && !tags.isEmpty()) {
            try {
                String tagStr = tags.get(RANDOM.nextInt(tags.size()));
                TagKey<Item> tagKey = ItemTags.create(ResourceLocation.parse(tagStr));
                List<Item> tagItems = new ArrayList<>();
                ForgeRegistries.ITEMS.tags().getTag(tagKey).forEach(tagItems::add);

                if (!tagItems.isEmpty()) {
                    return new ItemStack(tagItems.get(RANDOM.nextInt(tagItems.size())), count);
                }
            } catch (Exception ignored) {}
        }

        // 3. Fallback: Cobblestone
        return new ItemStack(Items.COBBLESTONE, count);
    }
}