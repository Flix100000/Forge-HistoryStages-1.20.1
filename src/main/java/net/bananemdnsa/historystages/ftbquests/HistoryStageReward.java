package net.bananemdnsa.historystages.ftbquests;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.network.Networking;
import net.bananemdnsa.historystages.util.IndividualStageData;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public class HistoryStageReward extends Reward {
    private String stage = "";
    private boolean remove = false;
    private boolean individual = false;

    public HistoryStageReward(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public RewardType getType() {
        return FTBQuestsIntegration.HISTORY_STAGE_REWARD;
    }

    @Override
    public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeData(nbt, provider);
        nbt.putString("stage", stage);
        nbt.putBoolean("remove", remove);
        nbt.putBoolean("individual", individual);
    }

    @Override
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);
        stage = nbt.getString("stage");
        remove = nbt.getBoolean("remove");
        individual = nbt.getBoolean("individual");
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buf) {
        super.writeNetData(buf);
        buf.writeUtf(stage);
        buf.writeBoolean(remove);
        buf.writeBoolean(individual);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buf) {
        super.readNetData(buf);
        stage = buf.readUtf(Short.MAX_VALUE);
        remove = buf.readBoolean();
        individual = buf.readBoolean();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString("stage", stage, v -> stage = v, "")
                .setNameKey("ftbquests.historystages.config.stage");
        config.addBool("remove", remove, v -> remove = v, false)
                .setNameKey("ftbquests.historystages.config.remove");
        config.addBool("individual", individual, v -> individual = v, false)
                .setNameKey("ftbquests.historystages.config.individual");
    }

    @Override
    public void claim(ServerPlayer player, boolean notify) {
        if (stage.isEmpty()) {
            return;
        }

        if (individual) {
            claimIndividual(player);
        } else {
            claimGlobal(player);
        }
    }

    private void claimGlobal(ServerPlayer player) {
        StageData data = StageData.get(player.serverLevel());
        if (remove) {
            data.removeStage(stage);
        } else {
            data.addStage(stage);
        }
        Networking.syncAll(player.server);
    }

    private void claimIndividual(ServerPlayer player) {
        IndividualStageData data = IndividualStageData.get(player.serverLevel());
        if (remove) {
            data.removeStage(player.getUUID(), stage);
        } else {
            data.addStage(player.getUUID(), stage);
        }
        Networking.syncPlayer(player);
    }

    @Override
    public MutableComponent getAltTitle() {
        String display = stage;
        var entry = individual ? StageManager.getIndividualStages().get(stage) : StageManager.getStages().get(stage);
        if (entry != null) {
            display = entry.getDisplayName();
        }
        return Component.literal(display).withStyle(remove ? ChatFormatting.RED : ChatFormatting.GREEN);
    }

    @Override
    public boolean ignoreRewardBlocking() {
        return true;
    }

    @Override
    protected boolean isIgnoreRewardBlockingHardcoded() {
        return true;
    }
}
