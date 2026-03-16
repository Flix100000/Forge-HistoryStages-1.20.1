package net.bananemdnsa.historystages.network;

import com.google.gson.Gson;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.function.Supplier;

public class SaveStagePacket {
    private static final Gson GSON = new Gson();

    private final String stageId;
    private final String stageJson;

    public SaveStagePacket(String stageId, StageEntry entry) {
        this.stageId = stageId;
        this.stageJson = entry.toJson();
    }

    private SaveStagePacket(String stageId, String stageJson) {
        this.stageId = stageId;
        this.stageJson = stageJson;
    }

    public static void encode(SaveStagePacket msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(msg.stageId);
        buffer.writeUtf(msg.stageJson, 65536);
    }

    public static SaveStagePacket decode(FriendlyByteBuf buffer) {
        String stageId = buffer.readUtf();
        String stageJson = buffer.readUtf(65536);
        return new SaveStagePacket(stageId, stageJson);
    }

    public static void handle(SaveStagePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;

            StageEntry entry = GSON.fromJson(msg.stageJson, StageEntry.class);
            if (entry == null) return;

            boolean success = StageManager.saveStage(msg.stageId, entry);
            if (success) {
                StageManager.reloadStages();
                // Sync updated stages to all players
                StageData data = StageData.get(player.serverLevel());
                PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(data.getUnlockedStages())));
                player.sendSystemMessage(Component.literal("§7[HistoryStages] §aStage '" + msg.stageId + "' saved successfully."));

                // Reload server resources
                player.server.reloadResources(player.server.getPackRepository().getSelectedIds());
            } else {
                player.sendSystemMessage(Component.literal("§7[HistoryStages] §cFailed to save stage '" + msg.stageId + "'."));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
