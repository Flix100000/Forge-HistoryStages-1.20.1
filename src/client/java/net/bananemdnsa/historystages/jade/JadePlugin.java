package net.bananemdnsa.historystages.jade;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.NbtMatcher;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.ClientIndividualStageCache;
import net.bananemdnsa.historystages.util.ClientStageCache;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@WailaPlugin
public class JadePlugin implements IWailaPlugin {
    private static final ResourceLocation LOCKED_BLOCK = HistoryStages.id("locked_block");
    private static final ResourceLocation LOCKED_ENTITY_ITEM = HistoryStages.id("locked_entity_item");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(LockedBlockProvider.INSTANCE, Block.class);
        registration.registerEntityComponent(LockedEntityItemProvider.INSTANCE, ItemFrame.class);
        registration.registerEntityComponent(LockedEntityItemProvider.INSTANCE, ArmorStand.class);
    }

    public enum LockedBlockProvider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!Config.CLIENT.jadeShowInfo) {
                return;
            }

            Block block = accessor.getBlock();
            ResourceLocation blockLocation = BuiltInRegistries.BLOCK.getKey(block);
            if (blockLocation == null) {
                return;
            }

            ItemStack blockItem = new ItemStack(block.asItem());
            if (blockItem.isEmpty()) {
                return;
            }

            ResourceLocation itemLocation = BuiltInRegistries.ITEM.getKey(blockItem.getItem());
            if (itemLocation == null) {
                return;
            }

            String itemId = itemLocation.toString();
            String modId = itemLocation.getNamespace();

            List<StageEntry> globalStages = new ArrayList<>();
            boolean globallyLocked = false;
            for (Map.Entry<String, StageEntry> stageEntry : StageManager.getStages().entrySet()) {
                StageEntry stage = stageEntry.getValue();
                String stageId = stageEntry.getKey();
                boolean isListed = (stage.getMods().contains(modId) && !stage.isModExcepted(itemId, blockItem))
                        || stage.getItems().contains(itemId)
                        || matchesNbtItem(stage, itemId, blockItem)
                        || blockItem.getTags().anyMatch(tag -> stage.getTags().contains(tag.location().toString()));
                if (isListed) {
                    globalStages.add(stage);
                    if (!ClientStageCache.isStageUnlocked(stageId)) {
                        globallyLocked = true;
                    }
                }
            }
            if (globallyLocked) {
                appendStageTooltip(tooltip, globalStages, false);
            }

            List<StageEntry> individualStages = new ArrayList<>();
            boolean individuallyLocked = false;
            for (Map.Entry<String, StageEntry> stageEntry : StageManager.getIndividualStages().entrySet()) {
                StageEntry stage = stageEntry.getValue();
                String stageId = stageEntry.getKey();
                boolean isListed = stage.getItems().contains(itemId)
                        || matchesNbtItem(stage, itemId, blockItem)
                        || blockItem.getTags().anyMatch(tag -> stage.getTags().contains(tag.location().toString()));
                if (isListed) {
                    individualStages.add(stage);
                    if (!ClientIndividualStageCache.isStageUnlocked(stageId)) {
                        individuallyLocked = true;
                    }
                }
            }
            if (individuallyLocked) {
                appendStageTooltip(tooltip, individualStages, true);
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
            if (!Config.CLIENT.jadeShowInfo) {
                return;
            }

            List<ItemStack> items = new ArrayList<>();
            if (accessor.getEntity() instanceof ItemFrame itemFrame) {
                ItemStack stack = itemFrame.getItem();
                if (!stack.isEmpty()) {
                    items.add(stack);
                }
            } else if (accessor.getEntity() instanceof ArmorStand armorStand) {
                armorStand.getArmorSlots().forEach(stack -> {
                    if (!stack.isEmpty()) {
                        items.add(stack);
                    }
                });
                armorStand.getHandSlots().forEach(stack -> {
                    if (!stack.isEmpty()) {
                        items.add(stack);
                    }
                });
            }

            if (items.isEmpty()) {
                return;
            }

            List<StageEntry> globalStages = new ArrayList<>();
            boolean globallyLocked = false;
            for (ItemStack stack : items) {
                ResourceLocation itemLocation = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (itemLocation == null) {
                    continue;
                }
                String itemId = itemLocation.toString();
                String modId = itemLocation.getNamespace();

                for (Map.Entry<String, StageEntry> stageEntry : StageManager.getStages().entrySet()) {
                    StageEntry stage = stageEntry.getValue();
                    String stageId = stageEntry.getKey();
                    boolean isListed = (stage.getMods().contains(modId) && !stage.isModExcepted(itemId, stack))
                            || stage.getItems().contains(itemId)
                            || matchesNbtItem(stage, itemId, stack)
                            || stack.getTags().anyMatch(tag -> stage.getTags().contains(tag.location().toString()));
                    if (isListed && !globalStages.contains(stage)) {
                        globalStages.add(stage);
                        if (!ClientStageCache.isStageUnlocked(stageId)) {
                            globallyLocked = true;
                        }
                    }
                }
            }
            if (globallyLocked) {
                appendStageTooltip(tooltip, globalStages, false);
            }

            List<StageEntry> individualStages = new ArrayList<>();
            boolean individuallyLocked = false;
            for (ItemStack stack : items) {
                ResourceLocation itemLocation = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (itemLocation == null) {
                    continue;
                }
                String itemId = itemLocation.toString();

                for (Map.Entry<String, StageEntry> stageEntry : StageManager.getIndividualStages().entrySet()) {
                    StageEntry stage = stageEntry.getValue();
                    String stageId = stageEntry.getKey();
                    boolean isListed = stage.getItems().contains(itemId)
                            || matchesNbtItem(stage, itemId, stack)
                            || stack.getTags().anyMatch(tag -> stage.getTags().contains(tag.location().toString()));
                    if (isListed && !individualStages.contains(stage)) {
                        individualStages.add(stage);
                        if (!ClientIndividualStageCache.isStageUnlocked(stageId)) {
                            individuallyLocked = true;
                        }
                    }
                }
            }
            if (individuallyLocked) {
                appendStageTooltip(tooltip, individualStages, true);
            }
        }

        @Override
        public ResourceLocation getUid() {
            return LOCKED_ENTITY_ITEM;
        }
    }

    private static void appendStageTooltip(ITooltip tooltip, List<StageEntry> requiredStages, boolean individual) {
        if (Config.CLIENT.jadeStageName) {
            tooltip.add(Component.literal(individual ? "Required Individual Progress:" : "Required Progress:")
                    .withStyle(ChatFormatting.DARK_RED));

            Map<String, StageEntry> stageMap = individual ? StageManager.getIndividualStages() : StageManager.getStages();
            for (StageEntry stage : requiredStages) {
                String stageId = stageMap.entrySet().stream()
                        .filter(entry -> entry.getValue().equals(stage))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse("");

                boolean unlocked = individual
                        ? ClientIndividualStageCache.isStageUnlocked(stageId)
                        : ClientStageCache.isStageUnlocked(stageId);

                if (requiredStages.size() > 1 && Config.CLIENT.jadeShowAllUntilComplete) {
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

    private static boolean matchesNbtItem(StageEntry stage, String itemId, ItemStack stack) {
        for (ItemEntry itemEntry : stage.getItemEntries()) {
            if (itemEntry.getId().equals(itemId) && itemEntry.hasNbt() && NbtMatcher.matches(stack, itemEntry.getNbt())) {
                return true;
            }
        }
        return false;
    }
}
