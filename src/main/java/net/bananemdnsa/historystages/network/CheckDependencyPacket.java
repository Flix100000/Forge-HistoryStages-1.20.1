package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.DependencyChecker;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> Server: Request dependency status check for a specific stage.
 * Server responds with SyncDependencyStatusPacket.
 */
public class CheckDependencyPacket {
    private final String stageId;
    private final boolean isIndividual;

    public CheckDependencyPacket(String stageId, boolean isIndividual) {
        this.stageId = stageId;
        this.isIndividual = isIndividual;
    }

    public static void encode(CheckDependencyPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.stageId);
        buf.writeBoolean(packet.isIndividual);
    }

    public static CheckDependencyPacket decode(FriendlyByteBuf buf) {
        return new CheckDependencyPacket(buf.readUtf(), buf.readBoolean());
    }

    public static void handle(CheckDependencyPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null)
                return;

            StageEntry entry = packet.isIndividual
                    ? StageManager.getIndividualStages().get(packet.stageId)
                    : StageManager.getStages().get(packet.stageId);

            if (entry == null)
                return;

            DependencyResult result = DependencyChecker.checkAll(entry, player, player.level(), null);
            PacketHandler.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new SyncDependencyStatusPacket(packet.stageId, result));
        });
        ctx.get().setPacketHandled(true);
    }
}
