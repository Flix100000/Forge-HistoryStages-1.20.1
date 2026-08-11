package net.bananemdnsa.historystages.client.scroll;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.compat.ScrollVariants;
import net.bananemdnsa.historystages.init.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Opens the open scroll document on right-click.
 *
 * <p>Client-only on purpose: the screen is pure display, built from the stage definitions and
 * unlock caches the client already holds. Nothing has to reach the server, so no common code ever
 * touches a client class.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT)
public final class OpenScrollHandler {

    private OpenScrollHandler() {}

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(ModItems.RESEARCH_SCROLL_OPEN.get())) return;

        String stageId = ScrollVariants.readStageResearch(stack);
        // An untagged scroll still opens: the screen says the stage is unknown, which beats an
        // item that silently does nothing when you click it.
        Minecraft.getInstance().setScreen(new OpenScrollScreen(stageId == null ? "" : stageId));
        event.setCanceled(true);
    }
}
