package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.LockBorderClientCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client: the set of {@link BoundingBox}es the player is currently gated by.
 * The client renders a force-field overlay on the faces of these boxes when the player
 * approaches them.
 */
public record SyncLockBordersPacket(List<BoundingBox> boxes) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncLockBordersPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "sync_lock_borders"));

    public static final StreamCodec<FriendlyByteBuf, SyncLockBordersPacket> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> {
                buf.writeVarInt(msg.boxes.size());
                for (BoundingBox bb : msg.boxes) {
                    buf.writeInt(bb.minX());
                    buf.writeInt(bb.minY());
                    buf.writeInt(bb.minZ());
                    buf.writeInt(bb.maxX());
                    buf.writeInt(bb.maxY());
                    buf.writeInt(bb.maxZ());
                }
            },
            buf -> {
                int n = buf.readVarInt();
                List<BoundingBox> boxes = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    int minX = buf.readInt(), minY = buf.readInt(), minZ = buf.readInt();
                    int maxX = buf.readInt(), maxY = buf.readInt(), maxZ = buf.readInt();
                    boxes.add(new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ));
                }
                return new SyncLockBordersPacket(boxes);
            }
    );

    public static void handle(SyncLockBordersPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) return;
            LockBorderClientCache.set(msg.boxes);
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
