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
    private final boolean individual;

    public SaveStagePacket(String stageId, StageEntry entry) {
        this(stageId, entry, false);
    }

    public SaveStagePacket(String stageId, StageEntry entry, boolean individual) {
        this.stageId = stageId;
        this.stageJson = entry.toJson();
        this.individual = individual;
    }

    private SaveStagePacket(String stageId, String stageJson, boolean individual) {
        this.stageId = stageId;
        this.stageJson = stageJson;
        this.individual = individual;
    }

    public static void encode(SaveStagePacket msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(msg.stageId);
        buffer.writeUtf(msg.stageJson, 65536);
        buffer.writeBoolean(msg.individual);
    }

    public static SaveStagePacket decode(FriendlyByteBuf buffer) {
        String stageId = buffer.readUtf();
        String stageJson = buffer.readUtf(65536);
        boolean individual = buffer.readBoolean();
        return new SaveStagePacket(stageId, stageJson, individual);
    }

    public static void handle(SaveStagePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;

            StageEntry entry = GSON.fromJson(msg.stageJson, StageEntry.class);
            if (entry == null) return;

            boolean success;
            if (msg.individual) {
                success = StageManager.saveIndividualStage(msg.stageId, entry);
            } else {
                success = StageManager.saveStage(msg.stageId, entry);
            }

            if (success) {
                StageManager.reloadStages();
                // Sync updated stages to all players
                StageData data = StageData.get(player.serverLevel());
                PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));
                PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(data.getUnlockedStages())));
                String prefix = msg.individual ? "Individual stage" : "Stage";
                player.sendSystemMessage(Component.literal("§7[HistoryStages] §a" + prefix + " '" + msg.stageId + "' saved successfully."));
            } else {
                player.sendSystemMessage(Component.literal("§7[HistoryStages] §cFailed to save stage '" + msg.stageId + "'."));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
