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
import net.bananemdnsa.historystages.network.SyncStagesPacket;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;

public class HistoryStageReward extends Reward {
    private String stage = "";
    private boolean remove = false;

    public HistoryStageReward(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public RewardType getType() {
        return FTBQuestsIntegration.HISTORY_STAGE_REWARD;
    }

    @Override
    public void writeData(CompoundTag nbt) {
        super.writeData(nbt);
        nbt.putString("stage", stage);
        nbt.putBoolean("remove", remove);
    }

    @Override
    public void readData(CompoundTag nbt) {
        super.readData(nbt);
        stage = nbt.getString("stage");
        remove = nbt.getBoolean("remove");
    }

    @Override
    public void writeNetData(FriendlyByteBuf buf) {
        super.writeNetData(buf);
        buf.writeUtf(stage);
        buf.writeBoolean(remove);
    }

    @Override
    public void readNetData(FriendlyByteBuf buf) {
        super.readNetData(buf);
        stage = buf.readUtf(Short.MAX_VALUE);
        remove = buf.readBoolean();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString("stage", stage, v -> stage = v, "")
                .setNameKey("ftbquests.historystages.config.stage");
        config.addBool("remove", remove, v -> remove = v, false)
                .setNameKey("ftbquests.historystages.config.remove");
    }

    @Override
    public void claim(ServerPlayer player, boolean notify) {
        if (stage.isEmpty()) return;

        StageData data = StageData.get(player.serverLevel());
        var entry = StageManager.getStages().get(stage);
        String displayName = entry != null ? entry.getDisplayName() : stage;

        if (remove) {
            if (!data.hasStage(stage)) return;
            data.removeStage(stage);
            data.setDirty();
            MinecraftForge.EVENT_BUS.post(new StageEvent.Locked(stage, displayName));

            // Broadcast lock effects (same as StageCommand)
            broadcastLockEffects(player, displayName);
        } else {
            if (data.hasStage(stage)) return;
            data.addStage(stage);
            data.setDirty();
            MinecraftForge.EVENT_BUS.post(new StageEvent.Unlocked(stage, displayName));

            // JEI hard-reload (same as Research Pedestal)
            if (player.server != null) {
                player.server.getCommands().performPrefixedCommand(
                        player.server.createCommandSourceStack().withSuppressedOutput(),
                        "history reload"
                );
            }

            // Broadcast unlock effects (same as Research Pedestal)
            broadcastUnlockEffects(player, displayName);
        }

        // Sync to all players
        StageData.SERVER_CACHE.clear();
        StageData.SERVER_CACHE.addAll(data.getUnlockedStages());
        PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(data.getUnlockedStages())));
        PacketHandler.reloadRecipesOnly(player.server);
    }

    private void broadcastUnlockEffects(ServerPlayer source, String stageName) {
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
            PacketHandler.sendToastToAll(new StageUnlockedToastPacket(stageName));
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
