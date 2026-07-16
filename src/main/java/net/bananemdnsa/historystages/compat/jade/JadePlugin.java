package net.bananemdnsa.historystages.compat.jade;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.block.MultiBlockResearchPedestalBlock;
import net.bananemdnsa.historystages.block.ResearchPedestalBlock;
import net.bananemdnsa.historystages.block.TieredPedestal;
import net.bananemdnsa.historystages.research.TierMatcher;
import net.bananemdnsa.historystages.client.display.HiddenDisplayResolver;
import net.bananemdnsa.historystages.data.display.DisplayMode;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.NbtMatcher;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.research.BoosterUtil;
import net.bananemdnsa.historystages.research.ResearchBooster;
import net.bananemdnsa.historystages.research.ResearchBoosterRegistry;
import net.bananemdnsa.historystages.client.cache.ClientIndividualStageCache;
import net.bananemdnsa.historystages.client.cache.ClientStageCache;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
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
        registration.registerBlockComponent(PedestalBoosterProvider.INSTANCE, MultiBlockResearchPedestalBlock.class);
        registration.registerEntityComponent(LockedEntityItemProvider.INSTANCE, ItemFrame.class);
        registration.registerEntityComponent(LockedEntityItemProvider.INSTANCE, ArmorStand.class);
    }

    public enum PedestalBoosterProvider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!Config.CLIENT.jadeShowInfo.get()) return;

            var selfState = accessor.getBlockState();
            int pedestalTier = selfState.getBlock() instanceof TieredPedestal tp ? tp.getTier() : 1;

            // Pos 1: directly below; Pos 2 (multiblock only): below the head.
            var level = accessor.getLevel();
            var pos = accessor.getPosition();
            describeBoosterAt(tooltip, level.getBlockState(pos.below()), pedestalTier);

            if (selfState.getBlock() instanceof MultiBlockResearchPedestalBlock) {
                var facing = selfState.getValue(MultiBlockResearchPedestalBlock.FACING);
                var headBelow = pos.relative(facing).below();
                describeBoosterAt(tooltip, level.getBlockState(headBelow), pedestalTier);
            }
        }

        private static void describeBoosterAt(ITooltip tooltip,
                net.minecraft.world.level.block.state.BlockState belowState, int pedestalTier) {
            ResearchBooster booster = ResearchBoosterRegistry.forBlockState(belowState).orElse(null);
            if (booster == null) return;

            boolean accepted = TierMatcher.matches(pedestalTier, booster.minTier(), booster.tierMode());
            if (accepted) {
                for (Component line : BoosterUtil.describe(booster)) {
                    tooltip.add(line.copy().withStyle(ChatFormatting.AQUA));
                }
            } else {
                tooltip.add(Component.translatable("jade.historystages.booster.tier_required",
                        TierMatcher.roman(booster.minTier()))
                        .withStyle(ChatFormatting.RED));
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
            Block block = accessor.getBlock();
            ResourceLocation blockLocation = BuiltInRegistries.BLOCK.getKey(block);
            if (blockLocation == null) return;

            // Check via the block's item form
            ItemStack blockItem = new ItemStack(block.asItem());
            if (blockItem.isEmpty()) return;

            // Hidden-display: override the Jade HUD title for name-locked blocks. Applies even
            // when the lock-info lines are disabled — this is the spoiler protection.
            HiddenDisplayResolver.Resolved hidden = HiddenDisplayResolver.resolve(blockItem);
            if (hidden.changesName()) {
                Component replacement = hidden.nameMode() == DisplayMode.REPLACE
                        ? Component.literal(hidden.nameText())
                        : Component.empty();
                tooltip.replace(JadeIds.CORE_OBJECT_NAME, replacement);
            }

            if (!Config.CLIENT.jadeShowInfo.get()) return;
            boolean suppressHints = !hidden.showLockHints();

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
                        blockItem.getTags().anyMatch(tag -> stage.getNbtFreeTags().contains(tag.location().toString())) ||
                        matchesNbtTag(stage, blockItem);

                if (isListed) {
                    totalRequiredStages.add(stage);
                    if (!ClientStageCache.isStageUnlocked(stageID)) {
                        isCurrentlyLocked = true;
                    }
                }
            }

            if (isCurrentlyLocked && !suppressHints) {
                appendStageTooltip(tooltip, totalRequiredStages, false);
            }

            // Individual stages — only shown when not in Phase 1 of a dual-phase lock
            if (!suppressHints && !StageLockHelper.isDualPhaseGloballyLockedClient(blockItem)) {
                List<StageEntry> individualRequiredStages = new ArrayList<>();
                boolean isIndividuallyLocked = false;

                for (Map.Entry<String, StageEntry> entry : StageManager.getIndividualStages().entrySet()) {
                    StageEntry stage = entry.getValue();
                    String stageID = entry.getKey();

                    boolean isListed = (stage.getMods().contains(modID) && !stage.isModExcepted(itemID, blockItem)) ||
                            stage.getItems().contains(itemID) ||
                            matchesNbtItem(stage, itemID, blockItem) ||
                            blockItem.getTags().anyMatch(tag -> stage.getNbtFreeTags().contains(tag.location().toString())) ||
                            matchesNbtTag(stage, blockItem);

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
                            stack.getTags().anyMatch(tag -> stage.getNbtFreeTags().contains(tag.location().toString())) ||
                            matchesNbtTag(stage, stack);

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
                                stack.getTags().anyMatch(tag -> stage.getNbtFreeTags().contains(tag.location().toString())) ||
                                matchesNbtTag(stage, stack);

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
                    String statusKey = unlocked ? "tooltip.historystages.status.unlocked" : "tooltip.historystages.status.locked";

                    tooltip.add(Component.literal(" • ")
                            .append(Component.literal(stage.getDisplayName()).withStyle(ChatFormatting.GOLD))
                            .append(Component.translatable(statusKey).withStyle(statusColor)));
                } else if (!unlocked) {
                    tooltip.add(Component.literal(" • ")
                            .append(Component.literal(stage.getDisplayName()).withStyle(ChatFormatting.GOLD)));
                }
            }
        } else {
            tooltip.add(Component.translatable("tooltip.historystages.contains_locked_items")
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

    private static boolean matchesNbtTag(StageEntry stage, ItemStack stack) {
        net.minecraft.world.item.Item item = stack.getItem();
        for (net.bananemdnsa.historystages.data.lock.NamedLockEntry tagEntry : stage.getTagEntries()) {
            if (tagEntry.hasNbt() && net.bananemdnsa.historystages.data.StageManager.tagEntryMatches(stack, item, tagEntry)) return true;
        }
        return false;
    }
}
