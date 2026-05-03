package net.bananemdnsa.historystages.init;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ModItems {
    public static final String CREATIVE_STAGE_ID = "_creative";

    public static final Item RESEARCH_SCROLL = new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)) {
        @Override
        public Component getName(ItemStack stack) {
            var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
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
            var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
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
}
