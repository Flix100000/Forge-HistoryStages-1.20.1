package net.bananemdnsa.historystages.ftbquests;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.AbstractBooleanTask;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.IndividualStageData;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class HistoryStageTask extends AbstractBooleanTask {
    private String stage = "";
    private boolean individual = false;
    private boolean negate = false;

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
        nbt.putBoolean("negate", negate);
    }

    @Override
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);
        stage = nbt.getString("stage");
        individual = nbt.getBoolean("individual");
        negate = nbt.getBoolean("negate");
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buf) {
        super.writeNetData(buf);
        buf.writeUtf(stage);
        buf.writeBoolean(individual);
        buf.writeBoolean(negate);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buf) {
        super.readNetData(buf);
        stage = buf.readUtf(Short.MAX_VALUE);
        individual = buf.readBoolean();
        negate = buf.readBoolean();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addEnum("stage", stage, v -> stage = v, buildStageNameMap(individual, stage), "")
                .setNameKey("ftbquests.task.historystages.history_stage.stage");
        config.addBool("individual", individual, v -> individual = v, false)
                .setNameKey("ftbquests.task.historystages.history_stage.individual");
        config.addBool("negate", negate, v -> negate = v, false)
                .setNameKey("ftbquests.task.historystages.history_stage.negate");
    }

    static NameMap<String> buildStageNameMap(boolean individual, String currentValue) {
        Map<String, StageEntry> source = individual
                ? StageManager.getIndividualStages()
                : StageManager.getStages();

        Set<String> ids = new LinkedHashSet<>();
        ids.add("");
        if (currentValue != null && !currentValue.isEmpty()) ids.add(currentValue);
        ids.addAll(source.keySet());

        List<String> list = new ArrayList<>(ids);

        return NameMap.of("", list)
                .id(Function.identity())
                .name(id -> {
                    if (id.isEmpty()) return Component.literal("(none)");
                    StageEntry entry = source.get(id);
                    return Component.literal(entry != null ? entry.getDisplayName() : id);
                })
                .create();
    }

    @Override
    public MutableComponent getAltTitle() {
        String key = negate
                ? "ftbquests.task.historystages.history_stage.title.negated"
                : "ftbquests.task.historystages.history_stage.title";
        return Component.translatable(key, resolveDisplayName());
    }

    @Override
    public Icon getAltIcon() {
        Map<String, StageEntry> source = individual
                ? StageManager.getIndividualStages()
                : StageManager.getStages();
        StageEntry entry = source.get(stage);
        String iconId = (entry != null && !entry.getIcon().isEmpty())
                ? entry.getIcon()
                : Config.COMMON.defaultStageIcon.get();
        if (iconId == null || iconId.isEmpty()) return super.getAltIcon();
        return ItemIcon.getItemIcon(iconId);
    }

    private String resolveDisplayName() {
        if (stage.isEmpty()) return stage;
        Map<String, StageEntry> source = individual
                ? StageManager.getIndividualStages()
                : StageManager.getStages();
        StageEntry entry = source.get(stage);
        return entry != null ? entry.getDisplayName() : stage;
    }

    @Override
    public boolean checkOnLogin() {
        return true;
    }

    @Override
    public boolean canSubmit(TeamData teamData, ServerPlayer player) {
        if (stage.isEmpty()) return false;
        boolean unlocked;
        if (individual) {
            if (!StageManager.getIndividualStages().containsKey(stage)) return false;
            unlocked = IndividualStageData.hasStageCached(player.getUUID(), stage);
        } else {
            if (!StageManager.getStages().containsKey(stage)) return false;
            unlocked = StageData.get(player.serverLevel()).hasStage(stage);
        }
        return negate != unlocked;
    }

    public String getStage() {
        return stage;
    }

    public boolean isIndividual() {
        return individual;
    }

    public boolean isNegated() {
        return negate;
    }

    /**
     * Called when a global History Stage is unlocked or locked. Submits or resets
     * matching tasks depending on their negate flag.
     */
    public static void onGlobalStageChanged(String stageId, boolean unlocked) {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null) return;

        for (HistoryStageTask task : file.collect(HistoryStageTask.class)) {
            if (task.isIndividual() || !stageId.equals(task.getStage())) continue;

            boolean shouldComplete = task.isNegated() != unlocked;
            for (ServerPlayer player : file.server.getPlayerList().getPlayers()) {
                TeamData teamData = file.getOrCreateTeamData(player);
                if (shouldComplete) {
                    task.submitTask(teamData, player, ItemStack.EMPTY);
                } else {
                    teamData.resetProgress(task);
                }
            }
        }
    }

    /**
     * Called when an individual History Stage is unlocked or locked for a specific player.
     */
    public static void onIndividualStageChanged(String stageId, ServerPlayer player, boolean unlocked) {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null) return;

        for (HistoryStageTask task : file.collect(HistoryStageTask.class)) {
            if (!task.isIndividual() || !stageId.equals(task.getStage())) continue;

            TeamData teamData = file.getOrCreateTeamData(player);
            boolean shouldComplete = task.isNegated() != unlocked;
            if (shouldComplete) {
                task.submitTask(teamData, player, ItemStack.EMPTY);
            } else {
                teamData.resetProgress(task);
            }
        }
    }
}
