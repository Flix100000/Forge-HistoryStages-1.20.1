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

    public static final RegistryObject<Item> RESEARCH_SCROLL = ITEMS.register("research_scroll",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)) {

                @Override
                public Component getName(ItemStack stack) {
                    if (stack.hasTag() && stack.getTag().contains("StageResearch")) {
                        String stageId = stack.getTag().getString("StageResearch");
                        var stage = StageManager.getStages().get(stageId);
                        if (stage != null) {
                            return Component.literal(stage.getDisplayName() + " Research Scroll")
                                    .withStyle(ChatFormatting.AQUA);
                        }
                    }
                    return super.getName(stack);
                }

                @Override
                public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
                    // Nur allgemeine Infos hier lassen
                    tooltip.add(Component.literal("Use this scroll in a Research Pedestial")
                            .withStyle(ChatFormatting.GOLD));

                    tooltip.add(Component.literal("to unlock new technologies.")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

                    // DER FORTSCHRITTS-CODE WURDE HIER ENTFERNT,
                    // DA ER SCHON IM TooltipEventHandler STEHT!
                }
            });

    public static final RegistryObject<Item> RESEARCH_PEDESTIAL_ITEM = ITEMS.register("research_pedestial",
            () -> new BlockItem(ModBlocks.RESEARCH_PEDESTIAL.get(), new Item.Properties()));

    public static void register(net.minecraftforge.eventbus.api.IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}