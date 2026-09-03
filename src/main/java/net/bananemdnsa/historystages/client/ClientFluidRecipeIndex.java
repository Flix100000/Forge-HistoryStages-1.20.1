package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.lock.FluidRecipeIndex;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;

/**
 * Keeps the client's copy of the fluid recipe index current.
 *
 * <p>The index has to exist on both sides, and for different reasons. The server needs it so a
 * machine finds no recipe; the client needs it so JEI and EMI grey the same recipe out, and so
 * the crafting preview agrees with what the server will allow. Both sides hold the same recipes
 * and the same stage definitions, so each can work the answer out locally — which is why this
 * costs no packet.
 *
 * <p>The server rebuilds on a tick. The client has no equivalent, so it is driven by the two
 * moments where the inputs actually change: the recipe list arriving, and a stage sync.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT)
public final class ClientFluidRecipeIndex {

    private ClientFluidRecipeIndex() {}

    /** Recipes arrived or were reloaded — the index is about something else now. */
    @SubscribeEvent
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        FluidRecipeIndex.markDirty();
        refresh();
    }

    /**
     * Rebuilds when stale. Called from the stage sync as well as from the recipe update, because
     * a pack whose first fluid entry is added in the editor changes no recipe at all — the index
     * would stay empty and the lock would look broken.
     *
     * <p>No-op before the player is in a world, and no-op when nothing is stale.
     */
    public static void refresh() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        try {
            FluidRecipeIndex.rebuildIfDirty(
                    mc.level.getRecipeManager().getRecipes(), mc.level.registryAccess());
        } catch (Exception e) {
            // A stale index greys out too little in a recipe viewer. It must not take the packet
            // handler down with it.
            DebugLogger.warn("Fluid Recipes",
                    "could not rebuild the client fluid recipe index: " + e);
        }
    }
}
