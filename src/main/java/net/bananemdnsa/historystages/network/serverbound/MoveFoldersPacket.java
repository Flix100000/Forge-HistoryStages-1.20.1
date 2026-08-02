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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Moves folders, with everything below them, into another folder. Takes a list because the
 * editor's organize mode can drag a whole selection at once, and one packet per folder
 * would mean one full reload-and-broadcast of every stage definition per folder.
 */
public record MoveFoldersPacket(boolean individual, List<String> paths, String targetParent) implements CustomPacketPayload {

    /** Upper bound on the decoded list, so a bad client cannot make the server allocate freely. */
    private static final int MAX_FOLDERS = 512;

    public static final CustomPacketPayload.Type<MoveFoldersPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "move_folders"));

    public static final StreamCodec<FriendlyByteBuf, MoveFoldersPacket> STREAM_CODEC =
            StreamCodec.of(MoveFoldersPacket::encode, MoveFoldersPacket::decode);

    private static void encode(FriendlyByteBuf buffer, MoveFoldersPacket msg) {
        buffer.writeBoolean(msg.individual);
        buffer.writeInt(msg.paths.size());
        for (String path : msg.paths) {
            buffer.writeUtf(path);
        }
        buffer.writeUtf(msg.targetParent);
    }

    private static MoveFoldersPacket decode(FriendlyByteBuf buffer) {
        boolean individual = buffer.readBoolean();
        int count = buffer.readInt();
        if (count < 0 || count > MAX_FOLDERS) {
            throw new IllegalArgumentException("MoveFoldersPacket carries " + count
                    + " paths, which is outside 0.." + MAX_FOLDERS + ".");
        }
        List<String> paths = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            paths.add(buffer.readUtf());
        }
        String targetParent = buffer.readUtf();
        return new MoveFoldersPacket(individual, paths, targetParent);
    }

    public static void handle(MoveFoldersPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;
            if (msg.paths.isEmpty()) return;
            if (!StagePaths.isValid(msg.targetParent)) return;

            boolean moved = true;
            for (String path : plan(msg.paths)) {
                if (!StagePaths.isValid(path) || path.isEmpty()) { moved = false; continue; }
                if (!StageManager.moveFolder(msg.individual, path, msg.targetParent)) moved = false;
            }

            // Reload and re-sync in both branches, exactly like the stage move: a rejected or
            // partial move still has to leave every client on the layout the server has.
            StageManager.reloadStages();
            net.bananemdnsa.historystages.events.lock.StructureLockHandler.invalidateAll();
            net.bananemdnsa.historystages.events.lock.BiomeLockHandler.invalidateAll();
            net.bananemdnsa.historystages.util.lock.StructureGenerationGate.rebuild();
            PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));

            if (moved) {
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.success(
                                "editor.historystages.toast.stages_moved.title",
                                "editor.historystages.toast.stages_moved.message",
                                MoveStagesPacket.targetLabel(msg.targetParent)),
                        player);
            } else {
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.error(
                                "editor.historystages.toast.move_failed.title",
                                "editor.historystages.toast.move_failed.message",
                                MoveStagesPacket.targetLabel(msg.targetParent)),
                        player);
            }
        });
    }

    /**
     * Orders the paths deepest first and drops any whose ancestor is also in the list.
     * Moving {@code a} before {@code a/b} would leave the second path pointing nowhere, and
     * a folder that travels with its moved parent must not be moved a second time.
     */
    static List<String> plan(List<String> paths) {
        List<String> sorted = new ArrayList<>(paths);
        sorted.sort(Comparator.comparingInt(StagePaths::depth).reversed());

        List<String> out = new ArrayList<>();
        for (String path : sorted) {
            boolean coveredByAncestor = false;
            for (String other : paths) {
                if (!other.equals(path) && path.startsWith(other + "/")) { coveredByAncestor = true; break; }
            }
            if (!coveredByAncestor) out.add(path);
        }
        return out;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
