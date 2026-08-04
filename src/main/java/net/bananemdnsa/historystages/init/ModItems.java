package net.bananemdnsa.historystages.init;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.*;
import net.bananemdnsa.historystages.client.cache.ClientDependencyCache;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.bananemdnsa.historystages.HistoryStages;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, HistoryStages.MOD_ID);

    public static final String CREATIVE_STAGE_ID = "_creative";

    public static final DeferredHolder<Item, Item> RESEARCH_SCROLL = ITEMS.register("research_scroll",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)) {

                @Override
                public Component getName(ItemStack stack) {
                    CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                    CompoundTag tag = customData.copyTag();
                    if (tag.contains("StageResearch")) {
                        String stageId = tag.getString("StageResearch");
                        var stage = StageManager.getStages().get(stageId);
                        if (stage == null) {
                            stage = StageManager.getIndividualStages().get(stageId);
                        }
                        if (stage != null) {
                            return Component.translatable("tooltip.historystages.research_scroll.named", stage.getDisplayName())
                                    .withStyle(ChatFormatting.AQUA);
                        }
                    }
                    return super.getName(stack);
                }

                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
                    CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                    CompoundTag tag = customData.copyTag();
                    if (tag.contains("StageResearch")) {
                        String stageId = tag.getString("StageResearch");
                        if (StageManager.isIndividualStage(stageId)) {
                            tooltip.add(Component.translatable("tooltip.historystages.scroll.individual")
                                    .withStyle(ChatFormatting.LIGHT_PURPLE));
                            if (tag.contains("OwnerName")) {
                                tooltip.add(Component.translatable("tooltip.historystages.scroll.owner", tag.getString("OwnerName"))
                                        .withStyle(ChatFormatting.GRAY));
                            }
                        }
                    }

                    tooltip.add(Component.translatable("tooltip.historystages.research_scroll.info1")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

                    tooltip.add(Component.translatable("tooltip.historystages.research_scroll.info2")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

                    // Min required pedestal tier (above dependencies block, when narrowing).
                    if (net.bananemdnsa.historystages.Config.CLIENT.showScrollTierTooltip.get()
                            && tag.contains("StageResearch")) {
                        String tierStageId = tag.getString("StageResearch");
                        StageEntry tierEntry = StageManager.isIndividualStage(tierStageId)
                                ? StageManager.getIndividualStages().get(tierStageId)
                                : StageManager.getStages().get(tierStageId);
                        if (tierEntry != null) {
                            int minTier = tierEntry.getMinPedestalTier();
                            net.bananemdnsa.historystages.research.TierMode mode =
                                    tierEntry.getPedestalTierMode();
                            if (minTier > 1
                                    || mode == net.bananemdnsa.historystages.research.TierMode.EXACT) {
                                String key = mode == net.bananemdnsa.historystages.research.TierMode.EXACT
                                        ? "tooltip.historystages.research_scroll.tier.exact"
                                        : "tooltip.historystages.research_scroll.tier.min";
                                tooltip.add(Component.translatable(key,
                                        net.bananemdnsa.historystages.research.TierMatcher.roman(minTier))
                                        .withStyle(ChatFormatting.GRAY));
                            }
                        }
                    }

                    // Show dependencies in tooltip
                    if (tag.contains("StageResearch")) {
                        String stageId = tag.getString("StageResearch");
                        boolean individual = StageManager.getStages().get(stageId) == null;
                        StageEntry entry = individual
                                ? StageManager.getIndividualStages().get(stageId)
                                : StageManager.getStages().get(stageId);
                        if (entry != null && entry.hasDependencies()) {
                            tooltip.add(Component.empty());
                            tooltip.add(Component.translatable("tooltip.historystages.scroll.dependencies")
                                    .withStyle(ChatFormatting.GOLD));

                            DependencyResult result = ClientDependencyCache.get(stageId, individual);
                            for (DependencyGroup group : entry.getDependencies()) {
                                if (group.isEmpty())
                                    continue;
                                String logic = group.getLogic();
                                // Show items
                                for (DependencyItem item : group.getItems()) {
                                    ResourceLocation rl = ResourceLocation.tryParse(item.getId());
                                    Item mcItem = rl != null ? BuiltInRegistries.ITEM.get(rl) : null;
                                    String name = mcItem != null ? mcItem.getDescription().getString() : item.getId();

                                    DependencyResult.EntryResult er = findResult(result, "item", item.getId());
                                    String icon = er != null ? (er.isFulfilled() ? "\u2714" : "\u2718") : "\u2022";
                                    String progress = er != null ? " (" + er.getCurrent() + "/" + er.getRequired() + ")" : "";

                                    tooltip.add(Component.literal("  " + icon + " " + name + progress)
                                            .withStyle(er != null && er.isFulfilled() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                                }
                                // Show stages
                                for (String sid : group.getStages()) {
                                    DependencyResult.EntryResult er = findResult(result, "stage", sid);
                                    String icon = er != null ? (er.isFulfilled() ? "\u2714" : "\u2718") : "\u2022";
                                    var se = StageManager.getStages().get(sid);
                                    String name = se != null ? se.getDisplayName() : sid;
                                    tooltip.add(Component.translatable("tooltip.historystages.dep.stage", icon, name)
                                            .withStyle(er != null && er.isFulfilled() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                                }
                                // Show individual stages
                                for (IndividualStageDep dep : group.getIndividualStages()) {
                                    DependencyResult.EntryResult er = findResult(result, "individual_stage", dep.getStageId());
                                    String icon = er != null ? (er.isFulfilled() ? "\u2714" : "\u2718") : "\u2022";
                                    var se = StageManager.getIndividualStages().get(dep.getStageId());
                                    String name = se != null ? se.getDisplayName() : dep.getStageId();
                                    tooltip.add(Component.literal("  " + icon + " " + name)
                                            .append(Component.translatable(dep.isAllEver() ? "tooltip.historystages.dep.all" : "tooltip.historystages.dep.online"))
                                            .withStyle(er != null && er.isFulfilled() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                                }
                                // Show XP level
                                XpLevelDep xp = group.getXpLevel();
                                if (xp != null && xp.getLevel() > 0) {
                                    DependencyResult.EntryResult er = findResult(result, "xp_level", "xp");
                                    String icon = er != null ? (er.isFulfilled() ? "\u2714" : "\u2718") : "\u2022";
                                    tooltip.add(Component.translatable("tooltip.historystages.dep.level", icon, xp.getLevel())
                                            .withStyle(er != null && er.isFulfilled() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                                }

                                if (entry.getDependencies().indexOf(group) < entry.getDependencies().size() - 1) {
                                    tooltip.add(Component.translatable("tooltip.historystages.dep.separator", logic)
                                            .withStyle(ChatFormatting.DARK_GRAY));
                                }
                            }
                        }
                    }
                }
            });

    public static final DeferredHolder<Item, Item> CREATIVE_SCROLL = ITEMS.register("creative_scroll",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)) {

                @Override
                public boolean isFoil(ItemStack stack) {
                    return true;
                }

                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.translatable("tooltip.historystages.creative_scroll.info1")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                    tooltip.add(Component.translatable("tooltip.historystages.creative_scroll.info2")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                }
            });

    public static final DeferredHolder<Item, Item> RESEARCH_PEDESTAL_ITEM = ITEMS.register("research_pedestal",
            () -> new BlockItem(ModBlocks.RESEARCH_PEDESTAL.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> RESEARCH_PEDESTAL_TIER_2_ITEM = ITEMS.register("research_pedestal_tier_2",
            () -> new BlockItem(ModBlocks.RESEARCH_PEDESTAL_TIER_2.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> RESEARCH_PEDESTAL_TIER_3_ITEM = ITEMS.register("research_pedestal_tier_3",
            () -> new BlockItem(ModBlocks.RESEARCH_PEDESTAL_TIER_3.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> RESEARCH_PEDESTAL_TIER_4_ITEM = ITEMS.register("research_pedestal_tier_4",
            () -> new BlockItem(ModBlocks.RESEARCH_PEDESTAL_TIER_4.get(), new Item.Properties()));

    /**
     * Finds a specific entry result in the cached dependency data.
     */
    private static @Nullable DependencyResult.EntryResult findResult(DependencyResult result, String type, String id) {
        if (result == null)
            return null;
        for (DependencyResult.GroupResult group : result.getGroups()) {
            for (DependencyResult.EntryResult entry : group.getEntries()) {
                if (entry.getType().equals(type) && entry.getId().equals(id)) {
                    return entry;
                }
            }
        }
        return null;
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
