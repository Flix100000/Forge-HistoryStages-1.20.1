package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.data.auto.AutoTriggerManager;
import net.bananemdnsa.historystages.data.auto.conditions.AdvancementTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.DimensionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Single Forge-bus listener that forwards relevant world events into
 * {@link AutoTriggerManager#process} so AUTO-mode stages can auto-unlock.
 *
 * <p>Registered on {@code NeoForge.EVENT_BUS} from {@code HistoryStages}'s
 * constructor. Polled triggers (biome/structure/playtime) piggyback the server
 * tick via {@link #pollPlayers(net.minecraft.server.MinecraftServer, int)}.
 */
public final class AutoTriggerEventBridge {

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ResourceLocation toLoc = event.getTo().location();
        String dimId = toLoc.toString();
        AutoTriggerManager.process(
                "dimension",
                t -> (t instanceof DimensionTrigger dt) && dimId.equals(dt.id()),
                player);
    }

    @SubscribeEvent
    public void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getAdvancement() == null) return;
        ResourceLocation advLoc = event.getAdvancement().id();
        if (advLoc == null) return;
        String advId = advLoc.toString();
        AutoTriggerManager.process(
                "advancement",
                t -> (t instanceof AdvancementTrigger at) && advId.equals(at.id()),
                player);
    }
}
