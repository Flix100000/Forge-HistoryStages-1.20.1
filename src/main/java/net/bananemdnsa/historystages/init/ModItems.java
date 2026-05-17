package net.bananemdnsa.historystages.init;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.DependencyItem;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.bananemdnsa.historystages.data.dependency.EntityKillDep;
import net.bananemdnsa.historystages.data.dependency.IndividualStageDep;
import net.bananemdnsa.historystages.data.dependency.StatDep;
import net.bananemdnsa.historystages.data.dependency.XpLevelDep;
import net.bananemdnsa.historystages.util.ClientDependencyCache;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ModItems {
    public static final String CREATIVE_STAGE_ID = "_creative";

    public static final Item RESEARCH_SCROLL = new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)) {
        @Override
        public Component getName(ItemStack stack) {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (tag.contains("StageResearch")) {
                String stageId = tag.getString("StageResearch");
                if (CREATIVE_STAGE_ID.equals(stageId)) {
                    return Component.literal("Creative Research Scroll").withStyle(ChatFormatting.AQUA);
                }
                StageEntry stage = StageManager.getStages().get(stageId);
                if (stage == null) {
                    stage = StageManager.getIndividualStages().get(stageId);
                }
                if (stage != null) {
                    return Component.literal(stage.getDisplayName() + " Research Scroll").withStyle(ChatFormatting.AQUA);
                }
            }
            return super.getName(stack);
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (tag.contains("StageResearch") && StageManager.isIndividualStage(tag.getString("StageResearch"))) {
                tooltip.add(Component.literal("Individual").withStyle(ChatFormatting.LIGHT_PURPLE));
                if (tag.contains("OwnerName")) {
                    tooltip.add(Component.literal("Owner: " + tag.getString("OwnerName")).withStyle(ChatFormatting.GRAY));
                }
            }
            tooltip.add(Component.translatable("tooltip.historystages.research_scroll.info1")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            tooltip.add(Component.translatable("tooltip.historystages.research_scroll.info2")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            appendDependencyTooltip(tag, tooltip);
        }
    };

    public static final Item CREATIVE_SCROLL = new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)) {
        @Override
        public boolean isFoil(ItemStack stack) {
            return true;
        }

        @Override
        public ItemStack getDefaultInstance() {
            return createCreativeScrollStack();
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.historystages.creative_scroll.info1")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            tooltip.add(Component.translatable("tooltip.historystages.creative_scroll.info2")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    };

    public static final BlockItem RESEARCH_PEDESTAL_ITEM = ModBlocks.createPedestalItem();

    private ModItems() {
    }

    public static ItemStack createCreativeScrollStack() {
        ItemStack stack = new ItemStack(CREATIVE_SCROLL);
        CompoundTag tag = new CompoundTag();
        tag.putString("StageResearch", CREATIVE_STAGE_ID);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static void register() {
        Registry.register(BuiltInRegistries.ITEM, HistoryStages.id("research_scroll"), RESEARCH_SCROLL);
        Registry.register(BuiltInRegistries.ITEM, HistoryStages.id("creative_scroll"), CREATIVE_SCROLL);
        Registry.register(BuiltInRegistries.ITEM, HistoryStages.id("research_pedestal"), RESEARCH_PEDESTAL_ITEM);
    }

    private static void appendDependencyTooltip(CompoundTag tag, List<Component> tooltip) {
        if (!Config.CLIENT.showDependenciesOnScroll || !tag.contains("StageResearch")) {
            return;
        }

        String stageId = tag.getString("StageResearch");
        StageEntry entry = StageManager.getStages().get(stageId);
        if (entry == null) {
            entry = StageManager.getIndividualStages().get(stageId);
        }
        if (entry == null || !entry.hasDependencies()) {
            return;
        }

        tooltip.add(Component.empty());
        tooltip.add(Component.literal("Dependencies:").withStyle(ChatFormatting.GOLD));

        DependencyResult result = ClientDependencyCache.get(stageId);
        List<DependencyGroup> groups = entry.getDependencies();
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            DependencyGroup group = groups.get(groupIndex);
            if (group.isEmpty()) {
                continue;
            }

            for (DependencyItem item : group.getItems()) {
                DependencyResult.EntryResult row = findResult(result, "item", item.getId());
                if (Config.CLIENT.hideFulfilledDependencies && isFulfilled(row)) {
                    continue;
                }
                tooltip.add(Component.literal("  " + statusIcon(row) + " " + item.getCount() + "x "
                        + displayItemName(item.getId()) + progressSuffix(row)).withStyle(statusColor(row)));
            }

            for (String requiredStage : group.getStages()) {
                DependencyResult.EntryResult row = findResult(result, "stage", requiredStage);
                if (Config.CLIENT.hideFulfilledDependencies && isFulfilled(row)) {
                    continue;
                }
                StageEntry required = StageManager.getStages().get(requiredStage);
                String name = required != null ? required.getDisplayName() : requiredStage;
                tooltip.add(Component.literal("  " + statusIcon(row) + " Stage: " + name)
                        .withStyle(statusColor(row)));
            }

            for (IndividualStageDep dep : group.getIndividualStages()) {
                DependencyResult.EntryResult row = findResult(result, "individual_stage", dep.getStageId());
                if (Config.CLIENT.hideFulfilledDependencies && isFulfilled(row)) {
                    continue;
                }
                StageEntry required = StageManager.getIndividualStages().get(dep.getStageId());
                String name = required != null ? required.getDisplayName() : dep.getStageId();
                tooltip.add(Component.literal("  " + statusIcon(row) + " " + name
                        + (dep.isAllEver() ? " (all ever)" : " (all online)")).withStyle(statusColor(row)));
            }

            for (String advancement : group.getAdvancements()) {
                DependencyResult.EntryResult row = findResult(result, "advancement", advancement);
                if (Config.CLIENT.hideFulfilledDependencies && isFulfilled(row)) {
                    continue;
                }
                tooltip.add(Component.literal("  " + statusIcon(row) + " Advancement: " + advancement)
                        .withStyle(statusColor(row)));
            }

            XpLevelDep xp = group.getXpLevel();
            if (xp != null && xp.getLevel() > 0) {
                DependencyResult.EntryResult row = findResult(result, "xp_level", "xp");
                if (!(Config.CLIENT.hideFulfilledDependencies && isFulfilled(row))) {
                    tooltip.add(Component.literal("  " + statusIcon(row) + " Level " + xp.getLevel()
                            + (xp.isConsume() ? " (consumed)" : "")).withStyle(statusColor(row)));
                }
            }

            for (EntityKillDep kill : group.getEntityKills()) {
                DependencyResult.EntryResult row = findResult(result, "entity_kill", kill.getEntityId());
                if (Config.CLIENT.hideFulfilledDependencies && isFulfilled(row)) {
                    continue;
                }
                tooltip.add(Component.literal("  " + statusIcon(row) + " " + kill.getCount() + "x "
                        + kill.getEntityId() + progressSuffix(row)).withStyle(statusColor(row)));
            }

            for (StatDep stat : group.getStats()) {
                DependencyResult.EntryResult row = findResult(result, "stat", stat.getStatId());
                if (Config.CLIENT.hideFulfilledDependencies && isFulfilled(row)) {
                    continue;
                }
                tooltip.add(Component.literal("  " + statusIcon(row) + " " + stat.getStatId() + " >= "
                        + stat.getMinValue() + progressSuffix(row)).withStyle(statusColor(row)));
            }

            if (groupIndex < groups.size() - 1) {
                tooltip.add(Component.literal("  --- " + group.getLogic() + " ---").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    @Nullable
    private static DependencyResult.EntryResult findResult(@Nullable DependencyResult result, String type, String id) {
        if (result == null) {
            return null;
        }
        for (DependencyResult.GroupResult group : result.getGroups()) {
            for (DependencyResult.EntryResult entry : group.getEntries()) {
                if (entry.getType().equals(type) && (entry.getId().equals(id) || entry.getDescription().contains(id))) {
                    return entry;
                }
            }
        }
        return null;
    }

    private static boolean isFulfilled(@Nullable DependencyResult.EntryResult result) {
        return result != null && result.isFulfilled();
    }

    private static String statusIcon(@Nullable DependencyResult.EntryResult result) {
        if (result == null) {
            return "\u2022";
        }
        return result.isFulfilled() ? "\u2714" : "\u2718";
    }

    private static ChatFormatting statusColor(@Nullable DependencyResult.EntryResult result) {
        return isFulfilled(result) ? ChatFormatting.GREEN : ChatFormatting.GRAY;
    }

    private static String progressSuffix(@Nullable DependencyResult.EntryResult result) {
        if (result == null || result.getRequired() <= 1) {
            return "";
        }
        return " (" + result.getCurrent() + "/" + result.getRequired() + ")";
    }

    private static String displayItemName(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return itemId;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? itemId : item.getDescription().getString();
    }
}
