package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.function.Supplier;

public class DeleteStagePacket {
    private final String stageId;
    private final boolean individual;

    public DeleteStagePacket(String stageId) {
        this(stageId, false);
    }

    public DeleteStagePacket(String stageId, boolean individual) {
        this.stageId = stageId;
        this.individual = individual;
    }

    public static void encode(DeleteStagePacket msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(msg.stageId);
        buffer.writeBoolean(msg.individual);
    }

    public static DeleteStagePacket decode(FriendlyByteBuf buffer) {
        String stageId = buffer.readUtf();
        boolean individual = buffer.readBoolean();
        return new DeleteStagePacket(stageId, individual);
    }

    public static void handle(DeleteStagePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;

            boolean success;
            if (msg.individual) {
                success = StageManager.deleteIndividualStage(msg.stageId);
            } else {
                success = StageManager.deleteStage(msg.stageId);
            }

            if (success) {
                StageManager.reloadStages();
                StageData data = StageData.get(player.serverLevel());
                PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));
                PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(data.getUnlockedStages())));
                String prefix = msg.individual ? "Individual stage" : "Stage";
                player.sendSystemMessage(Component.literal("§7[HistoryStages] §a" + prefix + " '" + msg.stageId + "' deleted."));
            } else {
                player.sendSystemMessage(Component.literal("§7[HistoryStages] §cFailed to delete stage '" + msg.stageId + "'."));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
