package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.client.OptionalRecipeViewHooks;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public final class PacketHandler {
    private PacketHandler() {
    }

    public static void sendToServer(Object packet) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return;
        }

        server.execute(() -> {
            if (packet instanceof SaveStagePacket saveStagePacket) {
                boolean success = StageManager.saveStage(saveStagePacket.stageId(), saveStagePacket.entry(), saveStagePacket.individual());
                StageManager.reloadStages();
                Networking.syncAll(server);
                if (success && Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage(Component.literal(
                            "[historystages] stage " + saveStagePacket.stageId() + " saved successfully"));
                }
            } else if (packet instanceof DeleteStagePacket deleteStagePacket) {
                StageManager.deleteStage(deleteStagePacket.stageId(), deleteStagePacket.individual());
                StageManager.reloadStages();
                Networking.syncAll(server);
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
            }
        });
    }
}
