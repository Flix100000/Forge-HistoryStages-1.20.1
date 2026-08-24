package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.api.stage.StageStates;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.TemporaryStageData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Revokes individual stages flagged {@code lose_on_death} when their owner dies.
 *
 * <p>Runs at {@link EventPriority#LOWEST} so anything that aborts the death gets
 * to cancel first — cancelled deaths never reach this handler.</p>
 *
 * <p>The re-lock happens on death rather than on respawn on purpose: the stage's
 * now-locked items then drop into the same pile as the rest of the death loot.
 * With {@code keepInventory} on, that means the stage's items drop while the
 * rest of the inventory stays, which is the point of the setting.</p>
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public final class StageDeathLossHandler {

    private StageDeathLossHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = player.serverLevel();
        UUID uuid = player.getUUID();
        IndividualStageData data = IndividualStageData.get(level);

        // Materialize first — relockIndividual mutates the player's stage set.
        List<String> lost = new ArrayList<>();
        for (String stageId : data.getUnlockedStages(uuid)) {
            StageEntry entry = StageManager.getIndividualStages().get(stageId);
            if (entry != null && entry.isLoseOnDeath()) {
                lost.add(stageId);
            }
        }
        if (lost.isEmpty()) return;

        TemporaryStageData temporary = TemporaryStageData.get(level);
        for (String stageId : lost) {
            StageStates.relockIndividual(stageId, player);
            // No-op unless the stage is in temporary mode with a running timer.
            temporary.expireIndividualEarly(uuid, stageId, HistoryStages::resolveTemporaryConfig);
        }
    }
}
