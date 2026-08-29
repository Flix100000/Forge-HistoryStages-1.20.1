package net.bananemdnsa.historystages.compat.ftbquests;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.api.stage.StageEvent;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.StageUnlockedToastPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncIndividualStagesPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStagesPacket;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
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
        config.add("stage", new StagePickerConfig(),
                        StagePickerConfig.toPrefixed(stage, individual),
                        v -> {
                            this.stage = StagePickerConfig.stripPrefix(v);
                            this.individual = StagePickerConfig.isIndividual(v);
                        }, "")
                .setNameKey("ftbquests.reward.historystages.history_stage.stage");
        config.addBool("remove", remove, v -> remove = v, false)
                .setNameKey("ftbquests.reward.historystages.history_stage.remove");
    }

    @Override
    public void claim(ServerPlayer player, boolean notify) {
        if (stage.isEmpty()) return;

        if (individual) {
            if (!StageManager.getIndividualStages().containsKey(stage)) return;
            claimIndividual(player);
        } else {
            if (!StageManager.getStages().containsKey(stage)) return;
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

            if (player.server != null) {
                player.server.getCommands().performPrefixedCommand(
                        player.server.createCommandSourceStack().withSuppressedOutput(),
                        "history reload"
                );
            }
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
            String iconId = (entry != null && !entry.getIcon().isEmpty()) ? entry.getIcon() : Config.VISUAL.defaultStageIcon.get();
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
            if (Config.GAMEPLAY.individualDropOnRevoke.get()) {
                StageLockHelper.dropLockedItemsForPlayer(player, stage);
            }

            // Notify only this player
            if (Config.VISUAL.individualBroadcastChat.get()) {
                player.sendSystemMessage(
                        Component.literal("[HistoryStages] ").withStyle(ChatFormatting.RED)
                                .append(Component.translatable("message.historystages.stage_forgotten", displayName).withStyle(ChatFormatting.WHITE))
                );
            }
            if (Config.VISUAL.individualUseSounds.get()) {
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.MASTER, 0.75F, 0.5F);
            }
        } else {
            if (data.hasStage(player.getUUID(), stage)) return;
            data.addStage(player.getUUID(), stage);
            data.setDirty();
            NeoForge.EVENT_BUS.post(new StageEvent.IndividualUnlocked(stage, displayName, player.getUUID()));

            // Notify only this player
            if (Config.VISUAL.individualBroadcastChat.get()) {
                String configChat = Config.VISUAL.individualUnlockMessageFormat.get();
                String finalChat = configChat.replace("{stage}", displayName)
                        .replace("{player}", player.getName().getString())
                        .replace("&", "\u00a7");
                player.sendSystemMessage(
                        Component.literal("[HistoryStages] ").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(finalChat))
                );
            }
            if (Config.VISUAL.individualUseActionbar.get()) {
                String configChat = Config.VISUAL.individualUnlockMessageFormat.get();
                String finalChat = configChat.replace("{stage}", displayName)
                        .replace("{player}", player.getName().getString())
                        .replace("&", "\u00a7");
                player.displayClientMessage(Component.literal(finalChat), true);
            }
            if (Config.VISUAL.individualUseSounds.get()) {
                player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.75F, 1.0F);
            }
            if (Config.VISUAL.individualUseToasts.get()) {
                var indEntry = StageManager.getIndividualStages().get(stage);
                String indIconId = (indEntry != null && !indEntry.getIcon().isEmpty())
                        ? indEntry.getIcon() : Config.VISUAL.defaultStageIcon.get();
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
        String configChat = Config.VISUAL.unlockMessageFormat.get();
        String finalChat = configChat.replace("{stage}", stageName).replace("&", "\u00a7");

        source.server.getPlayerList().getPlayers().forEach(player -> {
            if (Config.VISUAL.broadcastChat.get()) {
                player.sendSystemMessage(
                        Component.literal("[HistoryStages] ")
                                .withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(finalChat))
                );
            }
            if (Config.VISUAL.useActionbar.get()) {
                player.displayClientMessage(
                        Component.translatable("message.historystages.new_era_unlocked", stageName), true
                );
            }
            if (Config.VISUAL.useSounds.get()) {
                player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.75F, 1.0F);
            }
        });

        if (Config.VISUAL.useToasts.get()) {
            PacketHandler.sendToastToAll(new StageUnlockedToastPacket(stageName, iconId));
        }
    }

    private void broadcastLockEffects(ServerPlayer source, String stageName) {
        Component chatMsg = Component.literal("[HistoryStages] ").withStyle(ChatFormatting.RED)
                .append(Component.translatable("message.historystages.stage_forgotten", stageName).withStyle(ChatFormatting.WHITE));
        Component actionMsg = Component.translatable("message.historystages.stage_locked_action", stageName);

        source.server.getPlayerList().getPlayers().forEach(player -> {
            if (Config.VISUAL.broadcastChat.get()) {
                player.sendSystemMessage(chatMsg);
            }
            if (Config.VISUAL.useActionbar.get()) {
                player.displayClientMessage(actionMsg, true);
            }
            if (Config.VISUAL.useSounds.get()) {
                player.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.MASTER, 0.75F, 1.0F);
            }
        });
    }

    @Override
    public MutableComponent getAltTitle() {
        String displayName = resolveDisplayName();
        if (remove) {
            return Component.translatable("ftbquests.reward.historystages.history_stage.title.lock", displayName);
        }
        return Component.translatable("ftbquests.reward.historystages.history_stage.title.unlock", displayName);
    }

    @Override
    public Icon getAltIcon() {
        java.util.Map<String, StageEntry> source = individual
                ? StageManager.getIndividualStages()
                : StageManager.getStages();
        StageEntry entry = source.get(stage);
        String iconId = (entry != null && !entry.getIcon().isEmpty())
                ? entry.getIcon()
                : Config.VISUAL.defaultStageIcon.get();
        if (iconId == null || iconId.isEmpty()) return super.getAltIcon();
        return ItemIcon.getItemIcon(iconId);
    }

    private String resolveDisplayName() {
        if (stage.isEmpty()) return stage;
        java.util.Map<String, StageEntry> source = individual
                ? StageManager.getIndividualStages()
                : StageManager.getStages();
        StageEntry entry = source.get(stage);
        return entry != null ? entry.getDisplayName() : stage;
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
