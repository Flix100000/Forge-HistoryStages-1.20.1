package net.bananemdnsa.historystages.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server tells one reader which stage the scroll on a lectern carries.
 *
 * <p>The lectern block entity never syncs its item — it overrides neither {@code getUpdateTag} nor
 * {@code getUpdatePacket} — so the client cannot work this out for itself, however long it looks
 * at the block.
 *
 * <p>The screen open is routed through {@link DistExecutor} rather than called directly: this
 * packet class is loaded on the dedicated server too (for registration), and {@code Minecraft} is
 * a client-only type there. See {@code SyncConfigPacket} for the same pattern.
 */
public class OpenLecternScrollPacket {
    private final String stageId;
    private final BlockPos lecternPos;

    public OpenLecternScrollPacket(String stageId, BlockPos lecternPos) {
        this.stageId = stageId;
        this.lecternPos = lecternPos;
    }

    public static void encode(OpenLecternScrollPacket msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(msg.stageId);
        buffer.writeBlockPos(msg.lecternPos);
    }

    public static OpenLecternScrollPacket decode(FriendlyByteBuf buffer) {
        return new OpenLecternScrollPacket(buffer.readUtf(), buffer.readBlockPos());
    }

    public static void handle(OpenLecternScrollPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                net.minecraft.client.Minecraft.getInstance().setScreen(
                        new net.bananemdnsa.historystages.client.scroll.OpenScrollScreen(
                                msg.stageId, msg.lecternPos))));
        ctx.get().setPacketHandled(true);
    }
}
