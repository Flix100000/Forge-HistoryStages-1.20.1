package net.bananemdnsa.historystages.jade;

import net.astr0.historystages.api.StageDefinition;
import net.astr0.historystages.api.StageScope;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.*;
import net.bananemdnsa.historystages.util.ClientIndividualStageCache;
import net.bananemdnsa.historystages.util.ClientStageCache;
import net.minecraft.ChatFormatting;
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

import static net.bananemdnsa.historystages.util.ResourceLocationHelper.MOD_RESOURCE_LOCATION;

@WailaPlugin
public class JadePlugin implements IWailaPlugin {

    private static final ResourceLocation LOCKED_BLOCK = MOD_RESOURCE_LOCATION("locked_block");
    private static final ResourceLocation LOCKED_ENTITY_ITEM = MOD_RESOURCE_LOCATION("locked_entity_item");

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
            if (!Config.CLIENT.jadeShowInfo.get()) return;

            Block block = accessor.getBlock();

            // TODO: Review if NBT checks are necessary for a block match? Seems too niche to be worth supporting
            List<StageDefinition> totalRequiredStages = RuntimeStageManager.getInstance().getStagesForBlock(block);

            // This block doesn't have any locks. No need to go any further
            if (totalRequiredStages.isEmpty()) return;

            appendStageTooltip(tooltip, totalRequiredStages);
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

            List<StageDefinition> totalRequiredStages = new ArrayList<>();

            //TODO: Re-add compatibility for NBT checking
            for (ItemStack stack : items) {
                totalRequiredStages.addAll(RuntimeStageManager.getInstance().getStagesForItem(stack.getItem()));
            }

            appendStageTooltip(tooltip, totalRequiredStages);
        }

        @Override
        public ResourceLocation getUid() {
            return LOCKED_ENTITY_ITEM;
        }
    }

    // This handles both individual AND global stages AND also implements the expected Dual Phase behaviour
    private static void appendStageTooltip(ITooltip tooltip, List<StageDefinition> totalRequiredStages) {
        if (!Config.CLIENT.jadeStageName.get()) {
            tooltip.add(Component.literal("This contains locked items!")
                    .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
            return;
        }

        int globalEndIndex = 0;
        boolean anyGlobalLocked = false;

        // Scan the Global section at the start of the list
        for (StageDefinition stage : totalRequiredStages) {
            if (stage.getScope() != StageScope.GLOBAL) {
                break; // We've reached the Individual stages
            }

            globalEndIndex++;
            if (!isStageUnlocked(stage)) {
                anyGlobalLocked = true;
            }
        }

        // If any global is locked, we only show global stages (index 0 to globalEndIndex)
        // If all globals are unlocked, we only show individual stages (index globalEndIndex to size)
        int startIdx, endIdx;
        boolean showingGlobal;

        if (globalEndIndex > 0 && anyGlobalLocked) {
            startIdx = 0;
            endIdx = globalEndIndex;
            showingGlobal = true;
        } else {
            startIdx = globalEndIndex;
            endIdx = totalRequiredStages.size();
            showingGlobal = false;
        }

        // If the window is empty (e.g., all globals unlocked but no individual locks exist), exit
        if (startIdx >= endIdx) return;

        String header = showingGlobal ? "Required Global Progress:" : "Required Individual Progress:";
        tooltip.add(Component.literal(header).withStyle(ChatFormatting.DARK_RED));

        // 4. Render Stages
        boolean showAll = Config.CLIENT.jadeShowAllUntilComplete.get();
        for (int i = startIdx; i < endIdx; i++) {
            StageDefinition stage = totalRequiredStages.get(i);
            boolean unlocked = isStageUnlocked(stage);

            if (showAll) {
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
    }

    private static boolean isStageUnlocked(StageDefinition stage) {
        if (stage.getScope() == StageScope.GLOBAL) {
            return ClientStageCache.isStageUnlocked(stage.getName());
        } else {
            return ClientIndividualStageCache.isStageUnlocked(stage.getName());
        }
    }
}
