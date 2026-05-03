package net.bananemdnsa.historystages.network;

public record ToggleStageLockPacket(String stageId, boolean unlocked) {
}
