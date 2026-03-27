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

    public DeleteStagePacket(String stageId) {
        this.stageId = stageId;
    }

    public static void encode(DeleteStagePacket msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(msg.stageId);
    }

    public static DeleteStagePacket decode(FriendlyByteBuf buffer) {
        return new DeleteStagePacket(buffer.readUtf());
    }

    public static void handle(DeleteStagePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;

            boolean success = StageManager.deleteStage(msg.stageId);
            if (success) {
                StageManager.reloadStages();
                StageData data = StageData.get(player.serverLevel());
                PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));
                PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(data.getUnlockedStages())));
                player.sendSystemMessage(Component.literal("§7[HistoryStages] §aStage '" + msg.stageId + "' deleted."));
                player.server.reloadResources(player.server.getPackRepository().getSelectedIds());
            } else {
                player.sendSystemMessage(Component.literal("§7[HistoryStages] §cFailed to delete stage '" + msg.stageId + "'."));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
