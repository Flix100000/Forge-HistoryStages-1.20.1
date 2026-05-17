package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.data.StageEntry;

public record SaveStagePacket(String stageId, StageEntry entry, boolean individual) {
    public SaveStagePacket(String stageId, StageEntry entry) {
        this(stageId, entry, false);
    }
}
