package net.bananemdnsa.historystages.jade;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.block.ResearchPedestalBlock;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.NbtMatcher;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.research.BoosterUtil;
import net.bananemdnsa.historystages.research.ResearchBooster;
import net.bananemdnsa.historystages.research.ResearchBoosterRegistry;
import net.bananemdnsa.historystages.util.ClientIndividualStageCache;
import net.bananemdnsa.historystages.util.ClientStageCache;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.*;
import snownee.jade.api.config.IPluginConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@WailaPlugin
public class JadePlugin implements IWailaPlugin {

    private static final ResourceLocation LOCKED_BLOCK = ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "locked_block");
    private static final ResourceLocation LOCKED_ENTITY_ITEM = ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "locked_entity_item");
    private static final ResourceLocation PEDESTAL_BOOSTER = ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "pedestal_booster");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(LockedBlockProvider.INSTANCE, Block.class);
        registration.registerBlockComponent(PedestalBoosterProvider.INSTANCE, ResearchPedestalBlock.class);
        registration.registerEntityComponent(LockedEntityItemProvider.INSTANCE, ItemFrame.class);
        registration.registerEntityComponent(LockedEntityItemProvider.INSTANCE, ArmorStand.class);
    }

    public enum PedestalBoosterProvider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!Config.CLIENT.jadeShowInfo.get()) return;
            var below = accessor.getLevel().getBlockState(accessor.getPosition().below());
            ResearchBooster booster = ResearchBoosterRegistry.forBlockState(below).orElse(null);
            if (booster == null) return;

            for (Component line : BoosterUtil.describe(booster)) {
                tooltip.add(line.copy().withStyle(ChatFormatting.AQUA));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return PEDESTAL_BOOSTER;
        }
    }

    public enum LockedBlockProvider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!Config.CLIENT.jadeShowInfo.get()) return;

            Block block = accessor.getBlock();
            ResourceLocation blockLocation = BuiltInRegistries.BLOCK.getKey(block);
            if (blockLocation == null) return;

            // Check via the block's item form
            ItemStack blockItem = new ItemStack(block.asItem());
            if (blockItem.isEmpty()) return;

            ResourceLocation itemLocation = BuiltInRegistries.ITEM.getKey(blockItem.getItem());
            if (itemLocation == null) return;

            String itemID = itemLocation.toString();
            String modID = itemLocation.getNamespace();

            List<StageEntry> totalRequiredStages = new ArrayList<>();
            boolean isCurrentlyLocked = false;

            for (Map.Entry<String, StageEntry> entry : StageManager.getStages().entrySet()) {
                StageEntry stage = entry.getValue();
                String stageID = entry.getKey();

                boolean isListed = (stage.getMods().contains(modID) && !stage.isModExcepted(itemID, blockItem)) ||
                        stage.getItems().contains(itemID) ||
                        matchesNbtItem(stage, itemID, blockItem) ||
                        blockItem.getTags().anyMatch(tag -> stage.getTags().contains(tag.location().toString()));

                if (isListed) {
                    totalRequiredStages.add(stage);
                    if (!ClientStageCache.isStageUnlocked(stageID)) {
                        isCurrentlyLocked = true;
                    }
                }
            }

            if (isCurrentlyLocked) {
                appendStageTooltip(tooltip, totalRequiredStages, false);
            }

            // Individual stages — only shown when not in Phase 1 of a dual-phase lock
            if (!StageLockHelper.isDualPhaseGloballyLockedClient(blockItem)) {
                List<StageEntry> individualRequiredStages = new ArrayList<>();
                boolean isIndividuallyLocked = false;

                for (Map.Entry<String, StageEntry> entry : StageManager.getIndividualStages().entrySet()) {
                    StageEntry stage = entry.getValue();
                    String stageID = entry.getKey();

                    boolean isListed = (stage.getMods().contains(modID) && !stage.isModExcepted(itemID, blockItem)) ||
                            stage.getItems().contains(itemID) ||
                            matchesNbtItem(stage, itemID, blockItem) ||
                            blockItem.getTags().anyMatch(tag -> stage.getTags().contains(tag.location().toString()));

                    if (isListed) {
                        individualRequiredStages.add(stage);
                        if (!ClientIndividualStageCache.isStageUnlocked(stageID)) {
                            isIndividuallyLocked = true;
                        }
                    }
                }

                if (isIndividuallyLocked) {
                    appendStageTooltip(tooltip, individualRequiredStages, true);
                }
            }
        }

        @Override
        public ResourceLocation getUid() {
            return LOCKED_BLOCK;
        }
    }

    public enum LockedEntityItemProvider implements IEntityComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            if (!Config.CLIENT.jadeShowInfo.get()) return;

            List<ItemStack> items = new ArrayList<>();

            if (accessor.getEntity() instanceof ItemFrame itemFrame) {
                ItemStack item = itemFrame.getItem();
                if (!item.isEmpty()) items.add(item);
            } else if (accessor.getEntity() instanceof ArmorStand armorStand) {
                for (ItemStack stack : armorStand.getArmorSlots()) {
                    if (!stack.isEmpty()) items.add(stack);
                }
                for (ItemStack stack : armorStand.getHandSlots()) {
                    if (!stack.isEmpty()) items.add(stack);
                }
            }

            if (items.isEmpty()) return;

            List<StageEntry> totalRequiredStages = new ArrayList<>();
            boolean isCurrentlyLocked = false;

            for (ItemStack stack : items) {
                ResourceLocation itemLocation = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (itemLocation == null) continue;

                String itemID = itemLocation.toString();
                String modID = itemLocation.getNamespace();

                for (Map.Entry<String, StageEntry> entry : StageManager.getStages().entrySet()) {
                    StageEntry stage = entry.getValue();
                    String stageID = entry.getKey();

                    boolean isListed = (stage.getMods().contains(modID) && !stage.isModExcepted(itemID, stack)) ||
                            stage.getItems().contains(itemID) ||
                            matchesNbtItem(stage, itemID, stack) ||
                            stack.getTags().anyMatch(tag -> stage.getTags().contains(tag.location().toString()));

                    if (isListed && !totalRequiredStages.contains(stage)) {
                        totalRequiredStages.add(stage);
                        if (!ClientStageCache.isStageUnlocked(stageID)) {
                            isCurrentlyLocked = true;
                        }
                    }
                }
            }

            if (isCurrentlyLocked) {
                appendStageTooltip(tooltip, totalRequiredStages, false);
            }

            // Individual stages — only shown when not in Phase 1 of a dual-phase lock
            boolean anyDualPhaseGlobal = items.stream().anyMatch(StageLockHelper::isDualPhaseGloballyLockedClient);
            if (!anyDualPhaseGlobal) {
                List<StageEntry> individualRequiredStages = new ArrayList<>();
                boolean isIndividuallyLocked = false;

                for (ItemStack stack : items) {
                    ResourceLocation indItemLocation = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (indItemLocation == null) continue;

                    String indItemID = indItemLocation.toString();
                    String indModID = indItemLocation.getNamespace();

                    for (Map.Entry<String, StageEntry> entry : StageManager.getIndividualStages().entrySet()) {
                        StageEntry stage = entry.getValue();
                        String stageID = entry.getKey();

                        boolean isListed = (stage.getMods().contains(indModID) && !stage.isModExcepted(indItemID, stack)) ||
                                stage.getItems().contains(indItemID) ||
                                matchesNbtItem(stage, indItemID, stack) ||
                                stack.getTags().anyMatch(tag -> stage.getTags().contains(tag.location().toString()));

                        if (isListed && !individualRequiredStages.contains(stage)) {
                            individualRequiredStages.add(stage);
                            if (!ClientIndividualStageCache.isStageUnlocked(stageID)) {
                                isIndividuallyLocked = true;
                            }
                        }
                    }
                }

                if (isIndividuallyLocked) {
                    appendStageTooltip(tooltip, individualRequiredStages, true);
                }
            }
        }

        @Override
        public ResourceLocation getUid() {
            return LOCKED_ENTITY_ITEM;
        }
    }

    private static void appendStageTooltip(ITooltip tooltip, List<StageEntry> totalRequiredStages, boolean individual) {
        if (Config.CLIENT.jadeStageName.get()) {
            String header = individual ? "Required Individual Progress:" : "Required Progress:";
            tooltip.add(Component.literal(header).withStyle(ChatFormatting.DARK_RED));

            Map<String, StageEntry> stageMap = individual
                    ? StageManager.getIndividualStages()
                    : StageManager.getStages();

            for (StageEntry stage : totalRequiredStages) {
                String stageID = stageMap.entrySet().stream()
                        .filter(e -> e.getValue().equals(stage))
                        .map(Map.Entry::getKey).findFirst().orElse("");

                boolean unlocked = individual
                        ? ClientIndividualStageCache.isStageUnlocked(stageID)
                        : ClientStageCache.isStageUnlocked(stageID);
                boolean showAll = Config.CLIENT.jadeShowAllUntilComplete.get();

                if (totalRequiredStages.size() > 1 && showAll) {
                    ChatFormatting statusColor = unlocked ? ChatFormatting.GREEN : ChatFormatting.RED;
                    String statusText = unlocked ? " (Unlocked)" : " (Locked)";

                    tooltip.add(Component.literal(" • ")
                            .append(Component.literal(stage.getDisplayName()).withStyle(ChatFormatting.GOLD))
                            .append(Component.literal(statusText).withStyle(statusColor)));
                } else if (!unlocked) {
                    tooltip.add(Component.literal(" • ")
                            .append(Component.literal(stage.getDisplayName()).withStyle(ChatFormatting.GOLD)));
                }
            }
        } else {
            tooltip.add(Component.literal("This contains locked items!")
                    .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
        }
    }

    private static boolean matchesNbtItem(StageEntry stage, String itemID, ItemStack stack) {
        for (ItemEntry itemEntry : stage.getItemEntries()) {
            if (itemEntry.getId().equals(itemID) && itemEntry.hasNbt()) {
                if (NbtMatcher.matches(stack, itemEntry.getNbt())) return true;
            }
        }
        return false;
    }
}
