package net.bananemdnsa.historystages.ftbquests;

import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import net.bananemdnsa.historystages.HistoryStages;
import net.astr0.historystages.api.events.StageEvent;
import net.bananemdnsa.historystages.init.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

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
                HistoryStageTask.onStageUnlocked(event.getStageId())
        );

        MinecraftForge.EVENT_BUS.addListener((StageEvent.IndividualUnlocked event) -> {
            net.minecraft.server.level.ServerPlayer player = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()
                    .getPlayerList().getPlayer(event.getPlayerUUID());
            if (player != null) {
                HistoryStageTask.onIndividualStageUnlocked(event.getStageId(), player);
            }
        });
    }
}
