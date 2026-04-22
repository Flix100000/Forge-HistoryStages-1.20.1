package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@EventBusSubscriber(modid = "historystages")
public class LootLockHandler {
    private static final Random RANDOM = new Random();

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity().level().isClientSide()) return;

        if (event.getContainer().slots.isEmpty()) return;
        Container container = event.getContainer().slots.get(0).container;

        if (container == null) return;

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
                changed = true;
                replacedCount++;
            }
        }

        if (replacedCount > 0) {
            DebugLogger.runtime("Loot Lock", event.getEntity().getName().getString(),
                    "Replaced " + replacedCount + " locked item(s) in Lootr container");
        }

        if (changed) {
            event.getContainer().broadcastChanges();
        }
    }

    private static ItemStack getReplacement(int count) {
        // 1. Priority: random item from replacementItems list
        List<? extends String> list = Config.COMMON.replacementItems.get();
        if (list != null && !list.isEmpty()) {
            try {
                String randomId = list.get(RANDOM.nextInt(list.size()));
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(randomId));
                if (item != null && item != Items.AIR) {
                    return new ItemStack(item, count);
                }
            } catch (Exception ignored) {}
        }

        // 2. Priority: random item from replacementTags
        List<? extends String> tagList = Config.COMMON.replacementTags.get();
        if (tagList != null && !tagList.isEmpty()) {
            try {
                String tagStr = tagList.get(RANDOM.nextInt(tagList.size()));
                TagKey<Item> tagKey = ItemTags.create(ResourceLocation.parse(tagStr));
                List<Item> tagItems = new ArrayList<>();
                Optional<? extends Iterable<Holder<Item>>> tagOptional = BuiltInRegistries.ITEM.getTag(tagKey);
                tagOptional.ifPresent(holders -> {
                    for (Holder<Item> holder : holders) {
                        tagItems.add(holder.value());
                    }
                });
                if (!tagItems.isEmpty()) {
                    return new ItemStack(tagItems.get(RANDOM.nextInt(tagItems.size())), count);
                }
            } catch (Exception ignored) {}
        }

        return new ItemStack(Items.COBBLESTONE, count);
    }
}
