package net.bananemdnsa.historystages.network;

import net.minecraft.core.BlockPos;

public record DepositDependencyPacket(BlockPos blockPos, int groupIndex, String type, String data) {
}
