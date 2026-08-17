package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StagePaths;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Moves stage files into another folder. Stage IDs are file names, so nothing about the
 * stages themselves changes — only where their files live.
 */
public class MoveStagesPacket {

    /** Upper bound on the decoded list, so a bad client cannot make the server allocate freely. */
    private static final int MAX_STAGES = 512;

    private final boolean individual;
    private final List<String> stageIds;
    private final String targetFolder;

    public MoveStagesPacket(boolean individual, List<String> stageIds, String targetFolder) {
        this.individual = individual;
        this.stageIds = stageIds;
        this.targetFolder = targetFolder;
    }

    public static void encode(MoveStagesPacket msg, FriendlyByteBuf buffer) {
        buffer.writeBoolean(msg.individual);
        buffer.writeInt(msg.stageIds.size());
        for (String stageId : msg.stageIds) {
            buffer.writeUtf(stageId);
        }
        buffer.writeUtf(msg.targetFolder);
    }

    public static MoveStagesPacket decode(FriendlyByteBuf buffer) {
        boolean individual = buffer.readBoolean();
        int count = buffer.readInt();
        if (count < 0 || count > MAX_STAGES) {
            throw new IllegalArgumentException("MoveStagesPacket carries " + count
                    + " stage ids, which is outside 0.." + MAX_STAGES + ".");
        }
        List<String> stageIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            stageIds.add(buffer.readUtf());
        }
        String targetFolder = buffer.readUtf();
        return new MoveStagesPacket(individual, stageIds, targetFolder);
    }

    public static void handle(MoveStagesPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            if (!StagePaths.isValid(msg.targetFolder)) return;

            boolean moved = StageManager.moveStages(msg.individual, msg.stageIds, msg.targetFolder);

            // Both branches reload and re-sync: a partial move already changed the layout on
            // disk, and clients still describing the old one would save to paths that are gone.
            StageManager.reloadStages();
            net.bananemdnsa.historystages.events.lock.StructureLockHandler.invalidateAll();
            net.bananemdnsa.historystages.events.lock.BiomeLockHandler.invalidateAll();
            net.bananemdnsa.historystages.util.lock.StructureGenerationGate.rebuild();
            PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));

            String targetName = targetLabel(msg.targetFolder);
            if (moved) {
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.success(
                                "editor.historystages.toast.stages_moved.title",
                                "editor.historystages.toast.stages_moved.message",
                                targetName),
                        player);
            } else {
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.error(
                                "editor.historystages.toast.move_failed.title",
                                "editor.historystages.toast.move_failed.message",
                                targetName),
                        player);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /** Folder name for the toast; the tree root has no name of its own and shows as "/". */
    static String targetLabel(String folder) {
        String name = StagePaths.name(folder);
        return name.isEmpty() ? "/" : name;
    }
}
