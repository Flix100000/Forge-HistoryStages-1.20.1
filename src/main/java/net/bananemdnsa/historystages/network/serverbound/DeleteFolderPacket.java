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

/** Deletes a folder inside a stage tree; its content moves up one level. */
public record DeleteFolderPacket(boolean individual, String path) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DeleteFolderPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "delete_folder"));

    public static final StreamCodec<FriendlyByteBuf, DeleteFolderPacket> STREAM_CODEC =
            StreamCodec.of(DeleteFolderPacket::encode, DeleteFolderPacket::decode);

    private static void encode(FriendlyByteBuf buffer, DeleteFolderPacket msg) {
        buffer.writeBoolean(msg.individual);
        buffer.writeUtf(msg.path);
    }

    private static DeleteFolderPacket decode(FriendlyByteBuf buffer) {
        boolean individual = buffer.readBoolean();
        String path = buffer.readUtf();
        return new DeleteFolderPacket(individual, path);
    }

    public static void handle(DeleteFolderPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;
            if (!StagePaths.isValid(msg.path) || msg.path.isEmpty()) return;

            if (StageManager.deleteFolder(msg.individual, msg.path)) {
                StageManager.reloadStages();
                // Deleting a folder moves stage files on disk — invalidate the
                // structure-lock per-player cache so borders + screen overlay
                // reflect the change on the next server tick.
                net.bananemdnsa.historystages.events.lock.StructureLockHandler.invalidateAll();
                net.bananemdnsa.historystages.events.lock.BiomeLockHandler.invalidateAll();
                net.bananemdnsa.historystages.util.lock.StructureGenerationGate.rebuild();
                PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.success(
                                "editor.historystages.toast.folder_deleted.title",
                                "editor.historystages.toast.folder_deleted.message",
                                StagePaths.name(msg.path)),
                        player);
            } else {
                // A failed delete is not a no-op: the children are moved one at a time, so a
                // failure part-way through leaves the ones before it in their new place. Reload
                // and re-sync exactly like the success branch, otherwise the server and every
                // client keep describing the old layout and the next save writes to a path that
                // no longer exists.
                StageManager.reloadStages();
                net.bananemdnsa.historystages.events.lock.StructureLockHandler.invalidateAll();
                net.bananemdnsa.historystages.events.lock.BiomeLockHandler.invalidateAll();
                net.bananemdnsa.historystages.util.lock.StructureGenerationGate.rebuild();
                PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));
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
