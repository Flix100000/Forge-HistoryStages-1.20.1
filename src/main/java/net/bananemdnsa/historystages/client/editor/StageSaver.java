package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.client.cache.ClientDependencyCache;
import net.bananemdnsa.historystages.client.editor.toast.EditorToast;
import net.bananemdnsa.historystages.client.editor.toast.EditorToastHandler;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageJsonLimits;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.serverbound.SaveStagePacket;
import net.minecraft.network.chat.Component;

/**
 * Sends a stage to the server for saving. Stages that are too large to travel in one packet are
 * reported as a toast here, because letting them through would break the connection while the
 * packet is encoded.
 */
public final class StageSaver {

    private StageSaver() {}

    /** Returns false when the stage was too large and a toast was shown instead of saving. */
    public static boolean send(String stageId, StageEntry entry, boolean individual,
                               boolean duplicate, String folder) {
        String json = entry.toCompactJson();
        if (!StageJsonLimits.fitsSavePacket(json)) {
            EditorToastHandler.show(EditorToast.Level.ERROR,
                    Component.translatable("editor.historystages.toast.stage_too_large.title"),
                    Component.translatable("editor.historystages.toast.stage_too_large.message", stageId));
            return false;
        }
        PacketHandler.sendToServer(new SaveStagePacket(stageId, json, individual, duplicate,
                folder == null ? "" : folder));
        // Everything the graph knows about requirements is now potentially out of date, so throw
        // it away and let it be asked for again. Without this the graph shows a stage's
        // requirements as they were when its node was first opened, for the rest of the session —
        // the detail window only asks the server when it has nothing cached, and nothing else
        // invalidates this cache.
        //
        // The whole cache rather than this one stage: a save can change what another stage's
        // requirement rows read, because a stage reference is rendered with the referenced
        // stage's display name. Refilling costs one packet per node actually opened.
        ClientDependencyCache.clear();
        return true;
    }
}
