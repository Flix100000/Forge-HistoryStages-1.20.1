package net.bananemdnsa.historystages.ftbquests;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.events.StageEvent;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.StageUnlockedToastPacket;
import net.bananemdnsa.historystages.network.SyncIndividualStagesPacket;
import net.bananemdnsa.historystages.network.SyncStagesPacket;
import net.bananemdnsa.historystages.util.IndividualStageData;
import net.bananemdnsa.historystages.util.StageData;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.NeoForge;

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
        if (stage.isEmpty()) return;

        if (individual) {
            claimIndividual(player);
        } else {
            claimGlobal(player);
        }
    }

    private void claimGlobal(ServerPlayer player) {
        StageData data = StageData.get(player.serverLevel());
        var entry = StageManager.getStages().get(stage);
        String displayName = entry != null ? entry.getDisplayName() : stage;

        if (remove) {
            if (!data.hasStage(stage)) return;
            data.removeStage(stage);
            data.setDirty();
            NeoForge.EVENT_BUS.post(new StageEvent.Locked(stage, displayName));
            broadcastLockEffects(player, displayName);
        } else {
            if (data.hasStage(stage)) return;
            data.addStage(stage);
            data.setDirty();
            NeoForge.EVENT_BUS.post(new StageEvent.Unlocked(stage, displayName));

            if (player.server != null) {
                player.server.getCommands().performPrefixedCommand(
                        player.server.createCommandSourceStack().withSuppressedOutput(),
                        "history reload"
                );
            }
            String iconId = (entry != null && !entry.getIcon().isEmpty()) ? entry.getIcon() : Config.COMMON.defaultStageIcon.get();
        broadcastUnlockEffects(player, displayName, iconId);
        }

        PacketHandler.sendToAll(new SyncStagesPacket(data.getUnlockedStages()));
    }

    private void claimIndividual(ServerPlayer player) {
        IndividualStageData data = IndividualStageData.get(player.serverLevel());
        var entry = StageManager.getIndividualStages().get(stage);
        String displayName = entry != null ? entry.getDisplayName() : stage;

        if (remove) {
            if (!data.hasStage(player.getUUID(), stage)) return;
            data.removeStage(player.getUUID(), stage);
            data.setDirty();
            NeoForge.EVENT_BUS.post(new StageEvent.IndividualLocked(stage, displayName, player.getUUID()));

            // Drop locked items from inventory
            if (Config.COMMON.individualDropOnRevoke.get()) {
                StageLockHelper.dropLockedItemsForPlayer(player, stage);
            }

            // Notify only this player
            if (Config.COMMON.individualBroadcastChat.get()) {
                player.sendSystemMessage(
                        Component.literal("[HistoryStages] ").withStyle(ChatFormatting.RED)
                                .append(Component.literal("The knowledge of " + displayName + " has been forgotten...").withStyle(ChatFormatting.WHITE))
                );
            }
            if (Config.COMMON.individualUseSounds.get()) {
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.MASTER, 0.75F, 0.5F);
            }
        } else {
            if (data.hasStage(player.getUUID(), stage)) return;
            data.addStage(player.getUUID(), stage);
            data.setDirty();
            NeoForge.EVENT_BUS.post(new StageEvent.IndividualUnlocked(stage, displayName, player.getUUID()));

            // Notify only this player
            if (Config.COMMON.individualBroadcastChat.get()) {
                String configChat = Config.COMMON.individualUnlockMessageFormat.get();
                String finalChat = configChat.replace("{stage}", displayName)
                        .replace("{player}", player.getName().getString())
                        .replace("&", "\u00a7");
                player.sendSystemMessage(
                        Component.literal("[HistoryStages] ").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(finalChat))
                );
            }
            if (Config.COMMON.individualUseActionbar.get()) {
                String configChat = Config.COMMON.individualUnlockMessageFormat.get();
                String finalChat = configChat.replace("{stage}", displayName)
                        .replace("{player}", player.getName().getString())
                        .replace("&", "\u00a7");
                player.displayClientMessage(Component.literal(finalChat), true);
            }
            if (Config.COMMON.individualUseSounds.get()) {
                player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.75F, 1.0F);
            }
            if (Config.COMMON.individualUseToasts.get()) {
                var indEntry = StageManager.getIndividualStages().get(stage);
                String indIconId = (indEntry != null && !indEntry.getIcon().isEmpty())
                        ? indEntry.getIcon() : Config.COMMON.defaultStageIcon.get();
                PacketHandler.sendToastToPlayer(new StageUnlockedToastPacket(displayName, indIconId), player);
            }
        }

        // Sync individual stages to this player only
        PacketHandler.sendIndividualStagesToPlayer(
                new SyncIndividualStagesPacket(data.getUnlockedStages(player.getUUID())),
                player
        );
        // No recipe reload needed for individual stages
    }

    private void broadcastUnlockEffects(ServerPlayer source, String stageName, String iconId) {
        String configChat = Config.COMMON.unlockMessageFormat.get();
        String finalChat = configChat.replace("{stage}", stageName).replace("&", "\u00a7");

        source.server.getPlayerList().getPlayers().forEach(player -> {
            if (Config.COMMON.broadcastChat.get()) {
                player.sendSystemMessage(
                        Component.literal("[HistoryStages] ")
                                .withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(finalChat))
                );
            }
            if (Config.COMMON.useActionbar.get()) {
                player.displayClientMessage(
                        Component.literal("\u00a76New Era: " + stageName + " \u00a7aUnlocked!"), true
                );
            }
            if (Config.COMMON.useSounds.get()) {
                player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.75F, 1.0F);
            }
        });

        if (Config.COMMON.useToasts.get()) {
            PacketHandler.sendToastToAll(new StageUnlockedToastPacket(stageName, iconId));
        }
    }

    private void broadcastLockEffects(ServerPlayer source, String stageName) {
        Component chatMsg = Component.literal("[HistoryStages] ").withStyle(ChatFormatting.RED)
                .append(Component.literal("The knowledge of " + stageName + " has been forgotten...").withStyle(ChatFormatting.WHITE));
        Component actionMsg = Component.literal("\u00a7cStage Locked: " + stageName);

        source.server.getPlayerList().getPlayers().forEach(player -> {
            if (Config.COMMON.broadcastChat.get()) {
                player.sendSystemMessage(chatMsg);
            }
            if (Config.COMMON.useActionbar.get()) {
                player.displayClientMessage(actionMsg, true);
            }
            if (Config.COMMON.useSounds.get()) {
                player.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.MASTER, 0.75F, 1.0F);
            }
        });
    }

    @Override
    public MutableComponent getAltTitle() {
        if (remove) {
            return Component.translatable("ftbquests.historystages.reward.title.lock", stage);
        }
        return Component.translatable("ftbquests.historystages.reward.title.unlock", stage);
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
