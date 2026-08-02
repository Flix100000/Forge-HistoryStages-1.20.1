package net.bananemdnsa.historystages.network.serverbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StagePaths;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.EditorFeedbackPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStageDefinitionsPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Creates a folder inside a stage tree. {@code path} is relative to the tree root. */
public record CreateFolderPacket(boolean individual, String path) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CreateFolderPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "create_folder"));

    public static final StreamCodec<FriendlyByteBuf, CreateFolderPacket> STREAM_CODEC =
            StreamCodec.of(CreateFolderPacket::encode, CreateFolderPacket::decode);

    private static void encode(FriendlyByteBuf buffer, CreateFolderPacket msg) {
        buffer.writeBoolean(msg.individual);
        buffer.writeUtf(msg.path);
    }

    private static CreateFolderPacket decode(FriendlyByteBuf buffer) {
        boolean individual = buffer.readBoolean();
        String path = buffer.readUtf();
        return new CreateFolderPacket(individual, path);
    }

    public static void handle(CreateFolderPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;
            if (!StagePaths.isValid(msg.path) || msg.path.isEmpty()) return;

            if (StageManager.createFolder(msg.individual, msg.path)) {
                StageManager.reloadStages();
                PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.success(
                                "editor.historystages.toast.folder_created.title",
                                "editor.historystages.toast.folder_created.message",
                                StagePaths.name(msg.path)),
                        player);
            } else {
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.error(
                                "editor.historystages.toast.folder_failed.title",
                                "editor.historystages.toast.folder_failed.message",
                                StagePaths.name(msg.path)),
                        player);
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
