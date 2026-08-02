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

/** Renames a folder in place. Stage IDs are file names and stay untouched. */
public record RenameFolderPacket(boolean individual, String path, String newName) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RenameFolderPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "rename_folder"));

    public static final StreamCodec<FriendlyByteBuf, RenameFolderPacket> STREAM_CODEC =
            StreamCodec.of(RenameFolderPacket::encode, RenameFolderPacket::decode);

    private static void encode(FriendlyByteBuf buffer, RenameFolderPacket msg) {
        buffer.writeBoolean(msg.individual);
        buffer.writeUtf(msg.path);
        buffer.writeUtf(msg.newName);
    }

    private static RenameFolderPacket decode(FriendlyByteBuf buffer) {
        boolean individual = buffer.readBoolean();
        String path = buffer.readUtf();
        String newName = buffer.readUtf();
        return new RenameFolderPacket(individual, path, newName);
    }

    public static void handle(RenameFolderPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;
            if (!StagePaths.isValid(msg.path) || msg.path.isEmpty()) return;
            if (!StagePaths.isValidSegment(msg.newName)) return;

            if (StageManager.renameFolder(msg.individual, msg.path, msg.newName)) {
                StageManager.reloadStages();
                PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.success(
                                "editor.historystages.toast.folder_renamed.title",
                                "editor.historystages.toast.folder_renamed.message",
                                msg.newName),
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
