package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;

public record DeleteStagePacket(String stageId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DeleteStagePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "delete_stage"));

    public static final StreamCodec<FriendlyByteBuf, DeleteStagePacket> STREAM_CODEC =
            StreamCodec.of(DeleteStagePacket::encode, DeleteStagePacket::decode);

    private static void encode(FriendlyByteBuf buffer, DeleteStagePacket msg) {
        buffer.writeUtf(msg.stageId);
    }

    private static DeleteStagePacket decode(FriendlyByteBuf buffer) {
        return new DeleteStagePacket(buffer.readUtf());
    }

    public static void handle(DeleteStagePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            boolean success = StageManager.deleteStage(msg.stageId);
            if (success) {
                StageManager.reloadStages();
                StageData data = StageData.get(player.serverLevel());
                PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));
                PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(data.getUnlockedStages())));
                player.sendSystemMessage(Component.literal("§7[HistoryStages] §aStage '" + msg.stageId + "' deleted."));
                PacketHandler.reloadRecipesOnly(player.server);
            } else {
                player.sendSystemMessage(Component.literal("§7[HistoryStages] §cFailed to delete stage '" + msg.stageId + "'."));
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
