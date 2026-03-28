package net.bananemdnsa.historystages.ftbquests;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.AbstractBooleanTask;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public class HistoryStageTask extends AbstractBooleanTask {
    private String stage = "";

    public HistoryStageTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FTBQuestsIntegration.HISTORY_STAGE_TASK;
    }

    @Override
    public void writeData(CompoundTag nbt) {
        super.writeData(nbt);
        nbt.putString("stage", stage);
    }

    @Override
    public void readData(CompoundTag nbt) {
        super.readData(nbt);
        stage = nbt.getString("stage");
    }

    @Override
    public void writeNetData(FriendlyByteBuf buf) {
        super.writeNetData(buf);
        buf.writeUtf(stage);
    }

    @Override
    public void readNetData(FriendlyByteBuf buf) {
        super.readNetData(buf);
        stage = buf.readUtf(Short.MAX_VALUE);
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString("stage", stage, v -> stage = v, "")
                .setNameKey("ftbquests.historystages.config.stage");
    }

    @Override
    public MutableComponent getAltTitle() {
        return Component.translatable("ftbquests.historystages.task.title", stage);
    }

    @Override
    public boolean checkOnLogin() {
        return true;
    }

    @Override
    public boolean canSubmit(TeamData teamData, ServerPlayer player) {
        if (stage.isEmpty()) return false;
        StageData data = StageData.get(player.serverLevel());
        return data.hasStage(stage);
    }

    public String getStage() {
        return stage;
    }

    /**
     * Called when a History Stage is unlocked. Checks all HistoryStageTask instances
     * and submits matching ones for all online players.
     */
    public static void onStageUnlocked(String stageId) {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null) return;

        for (HistoryStageTask task : file.collect(HistoryStageTask.class)) {
            if (stageId.equals(task.getStage())) {
                for (ServerPlayer player : file.server.getPlayerList().getPlayers()) {
                    TeamData teamData = file.getOrCreateTeamData(player);
                    task.submitTask(teamData, player);
                }
            }
        }
    }
}
