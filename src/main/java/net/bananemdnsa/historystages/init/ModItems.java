package net.bananemdnsa.historystages.init;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageDefinition;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.DependencyItem;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.bananemdnsa.historystages.data.dependency.IndividualStageDep;
import net.bananemdnsa.historystages.data.dependency.XpLevelDep;
import net.bananemdnsa.historystages.util.ClientDependencyCache;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,
            HistoryStages.MOD_ID);

    public static final String CREATIVE_STAGE_ID = "_creative";

    public static final RegistryObject<Item> RESEARCH_SCROLL = ITEMS.register("research_scroll",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)) {

                @Override
                public Component getName(ItemStack stack) {
                    if (stack.hasTag() && stack.getTag().contains("StageResearch")) {
                        String stageId = stack.getTag().getString("StageResearch");
                        var stage = StageManager.getStages().get(stageId);
                        if (stage == null) {
                            stage = StageManager.getIndividualStages().get(stageId);
                        }
                        if (stage != null) {
                            return Component.literal(stage.getDisplayName() + " Research Scroll")
                                    .withStyle(ChatFormatting.AQUA);
                        }
                    }
                    return super.getName(stack);
                }

                @Override
                public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                        TooltipFlag flag) {
                    // Show individual mode and owner name
                    if (stack.hasTag() && stack.getTag().contains("StageResearch")) {
                        String stageId = stack.getTag().getString("StageResearch");
                        if (StageManager.isIndividualStage(stageId)) {
                            tooltip.add(Component.literal("Individual")
                                    .withStyle(ChatFormatting.LIGHT_PURPLE));
                            if (stack.getTag().contains("OwnerName")) {
                                tooltip.add(Component.literal("Owner: " + stack.getTag().getString("OwnerName"))
                                        .withStyle(ChatFormatting.GRAY));
                            }
                        }
                    }

                    tooltip.add(Component.translatable("tooltip.historystages.research_scroll.info1")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

                    tooltip.add(Component.translatable("tooltip.historystages.research_scroll.info2")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

                    // Show dependencies in tooltip
                    if (stack.hasTag() && stack.getTag().contains("StageResearch")) {
                        String stageId = stack.getTag().getString("StageResearch");
                        StageDefinition entry = StageManager.getStages().get(stageId);
                        if (entry == null)
                            entry = StageManager.getIndividualStages().get(stageId);
                        if (entry != null && entry.hasDependencies()) {
                            tooltip.add(Component.empty());
                            tooltip.add(Component.literal("Dependencies:")
                                    .withStyle(ChatFormatting.GOLD));

                            DependencyResult result = ClientDependencyCache.get(stageId);
                            for (DependencyGroup group : entry.getDependencies()) {
                                if (group.isEmpty())
                                    continue;
                                String logic = group.getLogic();
                                // Show items
                                for (DependencyItem item : group.getItems()) {
                                    ResourceLocation rl = ResourceLocation.tryParse(item.getId());
                                    String name = rl != null && ForgeRegistries.ITEMS.containsKey(rl)
                                            ? ForgeRegistries.ITEMS.getValue(rl).getDescription().getString()
                                            : item.getId();

                                    DependencyResult.EntryResult er = findResult(result, "item", item.getId());
                                    String icon = er != null ? (er.isFulfilled() ? "\u2714" : "\u2718") : "\u2022";
                                    String progress = er != null ? " (" + er.getCurrent() + "/" + er.getRequired() + ")"
                                            : "";

                                    tooltip.add(Component.literal("  " + icon + " " + name + progress)
                                            .withStyle(er != null && er.isFulfilled() ? ChatFormatting.GREEN
                                                    : ChatFormatting.GRAY));
                                }
                                // Show stages
                                for (String sid : group.getStages()) {
                                    DependencyResult.EntryResult er = findResult(result, "stage", sid);
                                    String icon = er != null ? (er.isFulfilled() ? "\u2714" : "\u2718") : "\u2022";
                                    var se = StageManager.getStages().get(sid);
                                    String name = se != null ? se.getDisplayName() : sid;
                                    tooltip.add(Component.literal("  " + icon + " Stage: " + name)
                                            .withStyle(er != null && er.isFulfilled() ? ChatFormatting.GREEN
                                                    : ChatFormatting.GRAY));
                                }
                                // Show individual stages
                                for (IndividualStageDep dep : group.getIndividualStages()) {
                                    DependencyResult.EntryResult er = findResult(result, "individual_stage",
                                            dep.getStageId());
                                    String icon = er != null ? (er.isFulfilled() ? "\u2714" : "\u2718") : "\u2022";
                                    var se = StageManager.getIndividualStages().get(dep.getStageId());
                                    String name = se != null ? se.getDisplayName() : dep.getStageId();
                                    tooltip.add(Component
                                            .literal("  " + icon + " " + name
                                                    + (dep.isAllEver() ? " (all)" : " (online)"))
                                            .withStyle(er != null && er.isFulfilled() ? ChatFormatting.GREEN
                                                    : ChatFormatting.GRAY));
                                }
                                // Show XP level
                                XpLevelDep xp = group.getXpLevel();
                                if (xp != null && xp.getLevel() > 0) {
                                    DependencyResult.EntryResult er = findResult(result, "xp_level", "xp");
                                    String icon = er != null ? (er.isFulfilled() ? "\u2714" : "\u2718") : "\u2022";
                                    tooltip.add(Component.literal("  " + icon + " Level " + xp.getLevel())
                                            .withStyle(er != null && er.isFulfilled() ? ChatFormatting.GREEN
                                                    : ChatFormatting.GRAY));
                                }

                                if (entry.getDependencies().indexOf(group) < entry.getDependencies().size() - 1) {
                                    tooltip.add(Component.literal("  --- " + logic + " ---")
                                            .withStyle(ChatFormatting.DARK_GRAY));
                                }
                            }
                        }
                    }
                }
            });

    public static final RegistryObject<Item> CREATIVE_SCROLL = ITEMS.register("creative_scroll",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)) {

                @Override
                public boolean isFoil(ItemStack stack) {
                    return true;
                }

                @Override
                public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                        TooltipFlag flag) {
                    tooltip.add(Component.translatable("tooltip.historystages.creative_scroll.info1")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                    tooltip.add(Component.translatable("tooltip.historystages.creative_scroll.info2")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                }
            });

    public static final RegistryObject<Item> RESEARCH_PEDESTAL_ITEM = ITEMS.register("research_pedestal",
            () -> new BlockItem(ModBlocks.RESEARCH_PEDESTAL.get(), new Item.Properties()));

    /**
     * Finds a specific entry result in the cached data.
     */
    private static @Nullable DependencyResult.EntryResult findResult(DependencyResult result, String type, String id) {
        if (result == null)
            return null;
        for (DependencyResult.GroupResult group : result.getGroups()) {
            for (DependencyResult.EntryResult entry : group.getEntries()) {
                if (entry.getType().equals(type) && (entry.getId().equals(id) || entry.getDescription().contains(id))) {
                    return entry;
                }
            }
        }
        return null;
    }

    public static void register(net.minecraftforge.eventbus.api.IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
