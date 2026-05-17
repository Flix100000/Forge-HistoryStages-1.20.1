package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.block.entity.ResearchPedestalBlockEntity;
import net.bananemdnsa.historystages.client.OptionalRecipeViewHooks;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.DependencyChecker;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.bananemdnsa.historystages.util.ClientDependencyCache;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.UUID;

public final class PacketHandler {
    private PacketHandler() {
    }

    public static void sendToServer(Object packet) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return;
        }

        UUID playerId = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
        server.execute(() -> {
            if (packet instanceof SaveStagePacket saveStagePacket) {
                boolean existed = saveStagePacket.individual()
                        ? StageManager.getIndividualStages().containsKey(saveStagePacket.stageId())
                        : StageManager.getStages().containsKey(saveStagePacket.stageId());
                boolean success = StageManager.saveStage(saveStagePacket.stageId(), saveStagePacket.entry(), saveStagePacket.individual());
                StageManager.reloadStages();
                Networking.syncAll(server);
                if (success) {
                    String prefix = saveStagePacket.individual() ? "Individual stage" : "Stage";
                    String action = existed ? "saved" : "created";
                    addClientChatMessage(successMessage(prefix + " '" + saveStagePacket.stageId()
                            + "' " + action + " successfully."));
                }
            } else if (packet instanceof DeleteStagePacket deleteStagePacket) {
                boolean success = StageManager.deleteStage(deleteStagePacket.stageId(), deleteStagePacket.individual());
                StageManager.reloadStages();
                Networking.syncAll(server);
                if (success) {
                    String prefix = deleteStagePacket.individual() ? "Individual stage" : "Stage";
                    addClientChatMessage(successMessage(prefix + " '" + deleteStagePacket.stageId()
                            + "' deleted successfully."));
                }
            } else if (packet instanceof ToggleStageLockPacket toggleStageLockPacket) {
                server.getAllLevels().forEach(level -> {
                    if (toggleStageLockPacket.unlocked()) {
                        StageData.get(level).addStage(toggleStageLockPacket.stageId());
                    } else {
                        StageData.get(level).removeStage(toggleStageLockPacket.stageId());
                    }
                });
                Networking.syncAll(server);
            } else if (packet instanceof SaveConfigPacket saveConfigPacket) {
                Config.applyEditorValues(saveConfigPacket.clientValues(), saveConfigPacket.commonValues());
                Config.save();
                Minecraft.getInstance().execute(OptionalRecipeViewHooks::refreshAll);
            } else if (packet instanceof CheckDependencyPacket checkDependencyPacket) {
                ServerPlayer player = playerId != null ? server.getPlayerList().getPlayer(playerId) : null;
                if (player == null) {
                    return;
                }
                StageEntry entry = checkDependencyPacket.individual()
                        ? StageManager.getIndividualStages().get(checkDependencyPacket.stageId())
                        : StageManager.getStages().get(checkDependencyPacket.stageId());
                if (entry == null) {
                    return;
                }
                CompoundTag deposited = null;
                BlockEntity blockEntity = player.level().getBlockEntity(checkDependencyPacket.blockPos());
                if (blockEntity instanceof ResearchPedestalBlockEntity pedestal) {
                    var scrollTag = pedestal.getScrollStack().getOrDefault(
                            net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                            net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
                    if (scrollTag.contains("DepositedDependencies")) {
                        deposited = scrollTag.getCompound("DepositedDependencies");
                    }
                }
                DependencyResult result = DependencyChecker.checkAll(entry, player, player.level(), deposited);
                Minecraft.getInstance().execute(() -> ClientDependencyCache.put(checkDependencyPacket.stageId(), result));
            } else if (packet instanceof DepositDependencyPacket depositDependencyPacket) {
                ServerPlayer player = playerId != null ? server.getPlayerList().getPlayer(playerId) : null;
                if (player == null) {
                    return;
                }
                BlockEntity blockEntity = player.level().getBlockEntity(depositDependencyPacket.blockPos());
                if (blockEntity instanceof ResearchPedestalBlockEntity pedestal) {
                    pedestal.handleDependencyDeposit(player, depositDependencyPacket.groupIndex(),
                            depositDependencyPacket.type(), depositDependencyPacket.data());
                    Networking.syncAll(server);
                }
            }
        });
    }

    private static Component successMessage(String text) {
        return Component.literal("[HistoryStages] ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(text).withStyle(ChatFormatting.GREEN));
    }

    private static void addClientChatMessage(Component message) {
        Minecraft.getInstance().execute(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gui != null) {
                minecraft.gui.getChat().addMessage(message);
            }
        });
    }
}
