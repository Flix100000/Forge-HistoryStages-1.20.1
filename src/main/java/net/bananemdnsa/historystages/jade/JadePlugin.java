package net.bananemdnsa.historystages.jade;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.ClientStageCache;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import snownee.jade.api.*;
import snownee.jade.api.config.IPluginConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@WailaPlugin
public class JadePlugin implements IWailaPlugin {

    private static final ResourceLocation LOCKED_BLOCK = new ResourceLocation(HistoryStages.MOD_ID, "locked_block");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(LockedBlockProvider.INSTANCE, Block.class);
    }

    public enum LockedBlockProvider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!Config.CLIENT.jadeShowInfo.get()) return;

            Block block = accessor.getBlock();
            ResourceLocation blockLocation = ForgeRegistries.BLOCKS.getKey(block);
            if (blockLocation == null) return;

            // Check via the block's item form
            ItemStack blockItem = new ItemStack(block.asItem());
            if (blockItem.isEmpty()) return;

            ResourceLocation itemLocation = ForgeRegistries.ITEMS.getKey(blockItem.getItem());
            if (itemLocation == null) return;

            String itemID = itemLocation.toString();
            String modID = itemLocation.getNamespace();

            List<StageEntry> totalRequiredStages = new ArrayList<>();
            boolean isCurrentlyLocked = false;

            for (Map.Entry<String, StageEntry> entry : StageManager.getStages().entrySet()) {
                StageEntry stage = entry.getValue();
                String stageID = entry.getKey();

                boolean isListed = stage.getMods().contains(modID) ||
                        stage.getItems().contains(itemID) ||
                        blockItem.getTags().anyMatch(tag -> stage.getTags().contains(tag.location().toString()));

                if (isListed) {
                    totalRequiredStages.add(stage);
                    if (!ClientStageCache.isStageUnlocked(stageID)) {
                        isCurrentlyLocked = true;
                    }
                }
            }

            if (isCurrentlyLocked) {
                if (Config.CLIENT.jadeStageName.get()) {
                    tooltip.add(Component.literal("Required Progress:").withStyle(net.minecraft.ChatFormatting.DARK_RED));

                    for (StageEntry stage : totalRequiredStages) {
                        String stageID = StageManager.getStages().entrySet().stream()
                                .filter(e -> e.getValue().equals(stage))
                                .map(Map.Entry::getKey).findFirst().orElse("");

                        boolean unlocked = ClientStageCache.isStageUnlocked(stageID);
                        boolean showAll = Config.CLIENT.jadeShowAllUntilComplete.get();

                        if (totalRequiredStages.size() > 1 && showAll) {
                            net.minecraft.ChatFormatting statusColor = unlocked ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED;
                            String statusText = unlocked ? " (Unlocked)" : " (Locked)";

                            tooltip.add(Component.literal(" • ")
                                    .append(Component.literal(stage.getDisplayName()).withStyle(net.minecraft.ChatFormatting.GOLD))
                                    .append(Component.literal(statusText).withStyle(statusColor)));
                        } else if (!unlocked) {
                            tooltip.add(Component.literal(" • ")
                                    .append(Component.literal(stage.getDisplayName()).withStyle(net.minecraft.ChatFormatting.GOLD)));
                        }
                    }
                } else {
                    tooltip.add(Component.literal("This block is currently locked!")
                            .withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.ITALIC));
                }
            }
        }

        @Override
        public ResourceLocation getUid() {
            return LOCKED_BLOCK;
        }
    }
}
