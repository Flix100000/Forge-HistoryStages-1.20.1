package net.bananemdnsa.historystages.ftbquests;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.AbstractBooleanTask;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import net.bananemdnsa.historystages.util.IndividualStageData;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public class HistoryStageTask extends AbstractBooleanTask {
    private String stage = "";
    private boolean individual = false;

    public HistoryStageTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FTBQuestsIntegration.HISTORY_STAGE_TASK;
    }

    @Override
    public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeData(nbt, provider);
        nbt.putString("stage", stage);
        nbt.putBoolean("individual", individual);
    }

    @Override
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);
        stage = nbt.getString("stage");
        individual = nbt.getBoolean("individual");
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buf) {
        super.writeNetData(buf);
        buf.writeUtf(stage);
        buf.writeBoolean(individual);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buf) {
        super.readNetData(buf);
        stage = buf.readUtf(Short.MAX_VALUE);
        individual = buf.readBoolean();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString("stage", stage, v -> stage = v, "")
                .setNameKey("ftbquests.historystages.config.stage");
        config.addBool("individual", individual, v -> individual = v, false)
                .setNameKey("ftbquests.historystages.config.individual");
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
        if (stage.isEmpty()) {
            return false;
        }
        if (individual) {
            return IndividualStageData.hasStageCached(player.getUUID(), stage);
        }
        return StageData.get(player.serverLevel()).hasStage(stage);
    }

    public String getStage() {
        return stage;
    }

    public boolean isIndividual() {
        return individual;
    }

    public static void onStageUnlocked(String stageId) {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null) {
            return;
        }

        for (HistoryStageTask task : file.collect(HistoryStageTask.class)) {
            if (!task.isIndividual() && stageId.equals(task.getStage())) {
                for (ServerPlayer player : file.server.getPlayerList().getPlayers()) {
                    task.submitTask(file.getOrCreateTeamData(player), player);
                }
            }
        }
    }

    public static void onIndividualStageUnlocked(String stageId, ServerPlayer player) {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null) {
            return;
        }

        for (HistoryStageTask task : file.collect(HistoryStageTask.class)) {
            if (task.isIndividual() && stageId.equals(task.getStage())) {
                task.submitTask(file.getOrCreateTeamData(player), player);
            }
        }
    }
}
