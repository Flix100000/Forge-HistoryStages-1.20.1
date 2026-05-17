package net.bananemdnsa.historystages.ftbquests;

import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.init.ModItems;
import net.fabricmc.api.ModInitializer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class FTBQuestsIntegration implements ModInitializer {
    public static TaskType HISTORY_STAGE_TASK;
    public static RewardType HISTORY_STAGE_REWARD;

    @Override
    public void onInitialize() {
        init();
    }

    public static void init() {
        if (HISTORY_STAGE_TASK != null || HISTORY_STAGE_REWARD != null) {
            return;
        }

        HISTORY_STAGE_TASK = TaskTypes.register(
                HistoryStages.id("history_stage"),
                HistoryStageTask::new,
                () -> ItemIcon.getItemIcon(new ItemStack(ModItems.RESEARCH_SCROLL))
        ).setDisplayName(Component.translatable("ftbquests.historystages.task.history_stage"));

        HISTORY_STAGE_REWARD = RewardTypes.register(
                HistoryStages.id("history_stage"),
                HistoryStageReward::new,
                () -> ItemIcon.getItemIcon(new ItemStack(ModItems.RESEARCH_SCROLL))
        ).setDisplayName(Component.translatable("ftbquests.historystages.reward.history_stage"));
    }

    public static void onStageUnlocked(String stageId) {
        HistoryStageTask.onStageUnlocked(stageId);
    }

    public static void onIndividualStageUnlocked(String stageId, net.minecraft.server.level.ServerPlayer player) {
        HistoryStageTask.onIndividualStageUnlocked(stageId, player);
    }
}
