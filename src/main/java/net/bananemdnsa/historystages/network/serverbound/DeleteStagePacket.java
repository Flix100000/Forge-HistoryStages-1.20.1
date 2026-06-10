package net.bananemdnsa.historystages.network.serverbound;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.SyncStagesPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStageDefinitionsPacket;
import net.bananemdnsa.historystages.network.clientbound.EditorFeedbackPacket;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;

public record DeleteStagePacket(String stageId, boolean individual) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DeleteStagePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "delete_stage"));

    public static final StreamCodec<FriendlyByteBuf, DeleteStagePacket> STREAM_CODEC =
            StreamCodec.of(DeleteStagePacket::encode, DeleteStagePacket::decode);

    public DeleteStagePacket(String stageId) {
        this(stageId, false);
    }

    private static void encode(FriendlyByteBuf buffer, DeleteStagePacket msg) {
        buffer.writeUtf(msg.stageId);
        buffer.writeBoolean(msg.individual);
    }

    private static DeleteStagePacket decode(FriendlyByteBuf buffer) {
        String stageId = buffer.readUtf();
        boolean individual = buffer.readBoolean();
        return new DeleteStagePacket(stageId, individual);
    }

    public static void handle(DeleteStagePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

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
                String titleKey = msg.individual
                        ? "editor.historystages.toast.individual_stage_deleted.title"
                        : "editor.historystages.toast.stage_deleted.title";
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.success(titleKey, "editor.historystages.toast.stage_deleted.message", msg.stageId),
                        player);
                PacketHandler.reloadRecipesOnly(player.server);
            } else {
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.error(
                                "editor.historystages.toast.stage_delete_failed.title",
                                "editor.historystages.toast.stage_delete_failed.message",
                                msg.stageId),
                        player);
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
