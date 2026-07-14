package net.bananemdnsa.historystages.compat.ftbquests;

import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.events.StageEvent;
import net.bananemdnsa.historystages.init.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.server.ServerLifecycleHooks;

public class FTBQuestsIntegration {

    public static TaskType HISTORY_STAGE_TASK;
    public static RewardType HISTORY_STAGE_REWARD;

    public static void init() {
        HISTORY_STAGE_TASK = TaskTypes.register(
                new ResourceLocation(HistoryStages.MOD_ID, "history_stage"),
                HistoryStageTask::new,
                () -> ItemIcon.getItemIcon(new ItemStack(ModItems.RESEARCH_SCROLL.get()))
        ).setDisplayName(Component.translatable("ftbquests.task.historystages.history_stage"));

        HISTORY_STAGE_REWARD = RewardTypes.register(
                new ResourceLocation(HistoryStages.MOD_ID, "history_stage"),
                HistoryStageReward::new,
                () -> ItemIcon.getItemIcon(new ItemStack(ModItems.RESEARCH_SCROLL.get()))
        ).setDisplayName(Component.translatable("ftbquests.reward.historystages.history_stage"));

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
