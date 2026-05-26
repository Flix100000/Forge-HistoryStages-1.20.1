package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class LootLockHandler {
    private static final Random RANDOM = new Random();

    private LootLockHandler() {
    }

    public static void onMenuOpened(ServerPlayer player, AbstractContainerMenu menu) {
        if (menu == null || menu.slots.isEmpty()) return;

        Container container = menu.slots.get(0).container;
        if (container == null) return;

        if (!container.getClass().getName().toLowerCase().contains("lootr")) return;

        int replacedCount = 0;
        boolean changed = false;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;

            if (StageLockHelper.isActionLockedForServer(stack, "loot")) {
                if (Config.COMMON.useReplacements) {
                    container.setItem(i, getReplacement(stack.getCount()));
                } else {
                    container.setItem(i, ItemStack.EMPTY);
                }
                replacedCount++;
                changed = true;
            }
        }

        if (changed) {
            DebugLogger.runtime("Loot Lock", player.getName().getString(),
                    "Replaced " + replacedCount + " locked item(s) in Lootr container [action: loot]");
            menu.broadcastChanges();
        }
    }

    private static ItemStack getReplacement(int count) {
        List<String> list = Config.COMMON.replacementItems;
        if (list != null && !list.isEmpty()) {
            try {
                String randomId = list.get(RANDOM.nextInt(list.size()));
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(randomId));
                if (item != null && item != Items.AIR) {
                    return new ItemStack(item, count);
                }
            } catch (Exception ignored) {
            }
        }

        List<String> tags = Config.COMMON.replacementTag;
        if (tags != null && !tags.isEmpty()) {
            try {
                String tagStr = tags.get(RANDOM.nextInt(tags.size()));
                TagKey<Item> tagKey = TagKey.create(Registries.ITEM, ResourceLocation.parse(tagStr));
                List<Item> tagItems = new ArrayList<>();
                BuiltInRegistries.ITEM.getTag(tagKey).ifPresent(named ->
                        named.forEach(holder -> tagItems.add(holder.value())));
                if (!tagItems.isEmpty()) {
                    return new ItemStack(tagItems.get(RANDOM.nextInt(tagItems.size())), count);
                }
            } catch (Exception ignored) {
            }
        }

        return new ItemStack(Items.COBBLESTONE, count);
    }
}
