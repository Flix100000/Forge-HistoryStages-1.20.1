package net.bananemdnsa.historystages.network;

import net.minecraft.core.BlockPos;

public record CheckDependencyPacket(String stageId, boolean individual, BlockPos blockPos) {
}
