package net.bananemdnsa.historystages.network.serverbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StagePaths;
import net.bananemdnsa.historystages.data.dependency.DependencyChecker;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.bananemdnsa.historystages.network.clientbound.SyncDependencyStatusPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: Request dependency status for a stage without a Research Pedestal — used by
 * the stage graph's detail panel, where there is no {@code BlockPos} to read a scroll from.
 *
 * <p>This is an ordinary player action, not an editor/admin one: any player looking at the graph
 * may ask for a stage's requirement status, so this handler does not gate on
 * {@code hasPermissions(2)}.
 *
 * <p>Runs the same {@link DependencyChecker} path {@link CheckDependencyPacket} uses, minus
 * everything pedestal-specific: no deposited-item NBT (there is no scroll), no booster cost
 * reduction (no pedestal tier), and the reply's {@code canDeposit} flags are always forced to
 * false — a panel that offers a deposit affordance the player cannot act on is worse than one
 * that offers none.
 */
public record RequestStageDependencyPacket(String stageId, boolean individual) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestStageDependencyPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "request_stage_dependency"));

    public static final StreamCodec<FriendlyByteBuf, RequestStageDependencyPacket> STREAM_CODEC =
            StreamCodec.of(RequestStageDependencyPacket::encode, RequestStageDependencyPacket::decode);

    private static void encode(FriendlyByteBuf buf, RequestStageDependencyPacket packet) {
        buf.writeUtf(packet.stageId);
        buf.writeBoolean(packet.individual);
    }

    private static RequestStageDependencyPacket decode(FriendlyByteBuf buf) {
        return new RequestStageDependencyPacket(buf.readUtf(), buf.readBoolean());
    }

    public static void handle(RequestStageDependencyPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
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
            PacketDistributor.sendToPlayer(player,
                    new SyncDependencyStatusPacket(packet.stageId, packet.individual, result));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
