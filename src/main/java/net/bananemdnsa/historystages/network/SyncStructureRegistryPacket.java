package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.ClientStructureRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → Client packet that syncs all registered structure IDs and structure
 * tag IDs so the editor UI can present searchable lists of what the server
 * recognizes.
 */
public record SyncStructureRegistryPacket(List<String> structureIds, List<String> structureTagIds)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncStructureRegistryPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "sync_structure_registry"));

    public static final StreamCodec<FriendlyByteBuf, SyncStructureRegistryPacket> STREAM_CODEC =
            StreamCodec.of(SyncStructureRegistryPacket::encode, SyncStructureRegistryPacket::decode);

    private static void encode(FriendlyByteBuf buf, SyncStructureRegistryPacket packet) {
        buf.writeVarInt(packet.structureIds.size());
        for (String id : packet.structureIds) buf.writeUtf(id);
        buf.writeVarInt(packet.structureTagIds.size());
        for (String id : packet.structureTagIds) buf.writeUtf(id);
    }

    private static SyncStructureRegistryPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) ids.add(buf.readUtf());
        int tagSize = buf.readVarInt();
        List<String> tags = new ArrayList<>(tagSize);
        for (int i = 0; i < tagSize; i++) tags.add(buf.readUtf());
        return new SyncStructureRegistryPacket(ids, tags);
    }

    public static void handle(SyncStructureRegistryPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientStructureRegistry.set(packet.structureIds, packet.structureTagIds));
    }

    public static SyncStructureRegistryPacket fromServer(ServerPlayer player) {
        Registry<Structure> registry = player.serverLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<String> ids = new ArrayList<>();
        for (ResourceLocation key : registry.keySet()) {
            ids.add(key.toString());
        }
        List<String> tagIds = new ArrayList<>();
        registry.getTagNames().forEach(tag -> tagIds.add(tag.location().toString()));
        return new SyncStructureRegistryPacket(ids, tagIds);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
