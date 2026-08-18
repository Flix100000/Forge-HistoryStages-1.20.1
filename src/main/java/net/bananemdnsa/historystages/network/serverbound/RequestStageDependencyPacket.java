package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StagePaths;
import net.bananemdnsa.historystages.data.dependency.DependencyChecker;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Client -&gt; Server: Request dependency status for a stage without a Research Pedestal — used by
 * the stage graph's detail panel, where there is no {@code BlockPos} to read a scroll from.
 *
 * <p>This is an ordinary player action, not an editor/admin one: any player looking at the graph
 * may ask for a stage's requirement status, so this handler does not gate on
 * {@code hasPermissions(2)}.
 *
 * <p>Runs the same {@link DependencyChecker} path {@code CheckDependencyPacket} uses, minus
 * everything pedestal-specific: no deposited-item NBT (there is no scroll), no booster cost
 * reduction (no pedestal tier), and the reply's {@code canDeposit} flags are always forced to
 * false — a panel that offers a deposit affordance the player cannot act on is worse than one
 * that offers none.
 */
public class RequestStageDependencyPacket {

    private final String stageId;
    private final boolean individual;

    public RequestStageDependencyPacket(String stageId, boolean individual) {
        this.stageId = stageId;
        this.individual = individual;
    }

    public static void encode(RequestStageDependencyPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.stageId);
        buf.writeBoolean(packet.individual);
    }

    public static RequestStageDependencyPacket decode(FriendlyByteBuf buf) {
        return new RequestStageDependencyPacket(buf.readUtf(), buf.readBoolean());
    }

    public static void handle(RequestStageDependencyPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            // Stage ids are file names; reject anything that could not be one before it ever
            // reaches a map lookup.
            if (!StagePaths.isValidSegment(packet.stageId)) return;

            StageEntry entry = packet.individual
                    ? StageManager.getIndividualStages().get(packet.stageId)
                    : StageManager.getStages().get(packet.stageId);
            if (entry == null) return;

            DependencyResult result = DependencyChecker
                    .checkAll(entry, player, player.level(), null, 0.0)
                    .withoutCanDeposit();
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new SyncDependencyStatusPacket(packet.stageId, packet.individual, result));
        });
        ctx.get().setPacketHandled(true);
    }
}
