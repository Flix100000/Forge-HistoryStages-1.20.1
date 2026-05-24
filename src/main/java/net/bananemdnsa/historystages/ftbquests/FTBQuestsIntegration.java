package net.bananemdnsa.historystages.ftbquests;

import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import net.astr0.historystages.api.events.StageEvent;
import net.bananemdnsa.historystages.init.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.server.ServerLifecycleHooks;

import static net.bananemdnsa.historystages.util.ResourceLocationHelper.MOD_RESOURCE_LOCATION;

public class FTBQuestsIntegration {

    public static TaskType HISTORY_STAGE_TASK;
    public static RewardType HISTORY_STAGE_REWARD;

    public static void init() {
        HISTORY_STAGE_TASK = TaskTypes.register(
                MOD_RESOURCE_LOCATION("history_stage"),
                HistoryStageTask::new,
                () -> ItemIcon.getItemIcon(new ItemStack(ModItems.RESEARCH_SCROLL.get()))
        ).setDisplayName(Component.translatable("ftbquests.historystages.task.history_stage"));

        HISTORY_STAGE_REWARD = RewardTypes.register(
                MOD_RESOURCE_LOCATION("history_stage"),
                HistoryStageReward::new,
                () -> ItemIcon.getItemIcon(new ItemStack(ModItems.RESEARCH_SCROLL.get()))
        ).setDisplayName(Component.translatable("ftbquests.historystages.reward.history_stage"));

        MinecraftForge.EVENT_BUS.addListener((StageEvent.Unlocked event) ->
                HistoryStageTask.onGlobalStageChanged(event.getStageId(), true)
        );

        MinecraftForge.EVENT_BUS.addListener((StageEvent.Locked event) ->
                HistoryStageTask.onGlobalStageChanged(event.getStageId(), false)
        );

        MinecraftForge.EVENT_BUS.addListener((StageEvent.IndividualUnlocked event) -> {
            ServerPlayer player = resolvePlayer(event.getPlayerUUID());
            if (player != null) {
                HistoryStageTask.onIndividualStageChanged(event.getStageId(), player, true);
            }
        });

        MinecraftForge.EVENT_BUS.addListener((StageEvent.IndividualLocked event) -> {
            ServerPlayer player = resolvePlayer(event.getPlayerUUID());
            if (player != null) {
                HistoryStageTask.onIndividualStageChanged(event.getStageId(), player, false);
            }
        });
    }

    private static ServerPlayer resolvePlayer(java.util.UUID uuid) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return server.getPlayerList().getPlayer(uuid);
    }
}
