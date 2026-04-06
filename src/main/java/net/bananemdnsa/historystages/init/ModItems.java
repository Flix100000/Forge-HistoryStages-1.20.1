package net.bananemdnsa.historystages.init;

import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.bananemdnsa.historystages.HistoryStages;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, HistoryStages.MOD_ID);

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
                public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
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
                }
            });

    public static final RegistryObject<Item> CREATIVE_SCROLL = ITEMS.register("creative_scroll",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)) {

                @Override
                public boolean isFoil(ItemStack stack) {
                    return true;
                }

                @Override
                public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.translatable("tooltip.historystages.creative_scroll.info1")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                    tooltip.add(Component.translatable("tooltip.historystages.creative_scroll.info2")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                }
            });

    public static final RegistryObject<Item> RESEARCH_PEDESTAL_ITEM = ITEMS.register("research_pedestal",
            () -> new BlockItem(ModBlocks.RESEARCH_PEDESTAL.get(), new Item.Properties()));

    public static void register(net.minecraftforge.eventbus.api.IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
