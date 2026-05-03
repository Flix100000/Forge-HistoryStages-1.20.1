package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.NbtMatcher;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.init.ModItems;
import net.bananemdnsa.historystages.util.ClientIndividualStageCache;
import net.bananemdnsa.historystages.util.ClientStageCache;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TooltipHandler {
    private TooltipHandler() {
    }

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, tooltipType, lines) -> {
            if (!Config.CLIENT.showTooltips || stack.isEmpty()) {
                return;
            }

            if (stack.is(ModItems.RESEARCH_SCROLL) || stack.is(ModItems.CREATIVE_SCROLL)) {
                return;
            }

            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null) {
                return;
            }

            addRequirementTooltip(lines, stack, id.toString(), id.getNamespace(),
                    StageManager.getStages(), true, "Required Progress:");

            if (Config.CLIENT.showIndividualTooltips) {
                addRequirementTooltip(lines, stack, id.toString(), id.getNamespace(),
                        StageManager.getIndividualStages(), false, "Required Individual Progress:");
            }
        });
    }

    private static void addRequirementTooltip(List<Component> lines, ItemStack stack, String itemId, String modId,
                                              Map<String, StageEntry> stages, boolean global, String header) {
        List<Map.Entry<String, StageEntry>> required = new ArrayList<>();
        for (Map.Entry<String, StageEntry> entry : stages.entrySet()) {
            if (isListed(entry.getValue(), itemId, modId, stack)) {
                required.add(entry);
            }
        }

        boolean locked = required.stream().anyMatch(entry -> global
                ? !ClientStageCache.isStageUnlocked(entry.getKey())
                : !ClientIndividualStageCache.isStageUnlocked(entry.getKey()));
        if (!locked) {
            return;
        }

        if (!Config.CLIENT.showStageName) {
            lines.add(Component.literal(global ? "This item is currently locked!" : "This item is individually locked!")
                    .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
            return;
        }

        lines.add(Component.literal(header).withStyle(ChatFormatting.DARK_RED));
        for (Map.Entry<String, StageEntry> entry : required) {
            boolean unlocked = global
                    ? ClientStageCache.isStageUnlocked(entry.getKey())
                    : ClientIndividualStageCache.isStageUnlocked(entry.getKey());
            if (!Config.CLIENT.showAllUntilComplete && unlocked) {
                continue;
            }
            ChatFormatting nameColor = global ? ChatFormatting.GOLD : ChatFormatting.GRAY;
            ChatFormatting stateColor = unlocked ? ChatFormatting.GREEN : ChatFormatting.RED;
            String state = unlocked ? " (Unlocked)" : " (Locked)";
            lines.add(Component.literal(" - ")
                    .append(Component.literal(entry.getValue().getDisplayName()).withStyle(nameColor))
                    .append(Component.literal(state).withStyle(stateColor)));
        }
    }

    private static boolean isListed(StageEntry stage, String itemId, String modId, ItemStack stack) {
        if (stage.getMods().contains(modId) && !stage.isModExcepted(itemId, stack)) {
            return true;
        }
        if (stage.getItems().contains(itemId) || matchesNbtItem(stage, itemId, stack)) {
            return true;
        }
        return stack.getItem().builtInRegistryHolder().tags()
                .map(TagKey::location)
                .map(ResourceLocation::toString)
                .anyMatch(stage.getTags()::contains);
    }

    private static boolean matchesNbtItem(StageEntry stage, String itemId, ItemStack stack) {
        for (ItemEntry itemEntry : stage.getItemEntries()) {
            if (itemEntry.getId().equals(itemId) && itemEntry.hasNbt() && NbtMatcher.matches(stack, itemEntry.getNbt())) {
                return true;
            }
        }
        return false;
    }
}
