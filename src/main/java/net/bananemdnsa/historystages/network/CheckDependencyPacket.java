package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.data.StageDefinition;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.DependencyChecker;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> Server: Request dependency status check for a specific stage.
 * Server responds with SyncDependencyStatusPacket.
 */
public class CheckDependencyPacket {
    private final String stageId;
    private final boolean isIndividual;
    private final BlockPos pos;

    public CheckDependencyPacket(String stageId, boolean isIndividual, BlockPos pos) {
        this.stageId = stageId;
        this.isIndividual = isIndividual;
        this.pos = pos;
    }

    public static void encode(CheckDependencyPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.stageId);
        buf.writeBoolean(packet.isIndividual);
        buf.writeBlockPos(packet.pos);
    }

    public static CheckDependencyPacket decode(FriendlyByteBuf buf) {
        return new CheckDependencyPacket(buf.readUtf(), buf.readBoolean(), buf.readBlockPos());
    }

    public static void handle(CheckDependencyPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null)
                return;

            StageDefinition entry = packet.isIndividual
                    ? StageManager.getIndividualStages().get(packet.stageId)
                    : StageManager.getStages().get(packet.stageId);

            if (entry == null)
                return;

            CompoundTag depositedTag = null;
            BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (be instanceof net.bananemdnsa.historystages.block.entity.ResearchPedestalBlockEntity pedestal) {
                depositedTag = pedestal.getScrollStack().hasTag()
                        && pedestal.getScrollStack().getTag().contains("DepositedDependencies")
                                ? pedestal.getScrollStack().getTag().getCompound("DepositedDependencies")
                                : null;
            }

            DependencyResult result = DependencyChecker.checkAll(entry, player, player.level(), depositedTag);
            PacketHandler.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new SyncDependencyStatusPacket(packet.stageId, result));
        });
        ctx.get().setPacketHandled(true);
    }
}
