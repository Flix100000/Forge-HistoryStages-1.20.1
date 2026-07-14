package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.client.LockBorderClientCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -> client: the set of {@link BoundingBox}es the player is currently gated by.
 * The client renders a force-field overlay on the faces of these boxes when the player
 * approaches them, and a screen tint when the player is inside any of them.
 */
public class SyncLockBordersPacket {

    private final List<BoundingBox> boxes;

    public SyncLockBordersPacket(List<BoundingBox> boxes) {
        this.boxes = boxes;
    }

    public static void encode(SyncLockBordersPacket msg, FriendlyByteBuf buffer) {
        buffer.writeVarInt(msg.boxes.size());
        for (BoundingBox bb : msg.boxes) {
            buffer.writeVarInt(bb.minX());
            buffer.writeVarInt(bb.minY());
            buffer.writeVarInt(bb.minZ());
            buffer.writeVarInt(bb.maxX());
            buffer.writeVarInt(bb.maxY());
            buffer.writeVarInt(bb.maxZ());
        }
    }

    public static SyncLockBordersPacket decode(FriendlyByteBuf buffer) {
        int n = buffer.readVarInt();
        List<BoundingBox> boxes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int minX = buffer.readVarInt();
            int minY = buffer.readVarInt();
            int minZ = buffer.readVarInt();
            int maxX = buffer.readVarInt();
            int maxY = buffer.readVarInt();
            int maxZ = buffer.readVarInt();
            boxes.add(new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ));
        }
        return new SyncLockBordersPacket(boxes);
    }

    public static void handle(SyncLockBordersPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) return;
            LockBorderClientCache.set(msg.boxes);
        });
        ctx.get().setPacketHandled(true);
    }
}
