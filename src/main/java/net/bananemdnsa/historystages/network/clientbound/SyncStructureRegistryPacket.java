package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.client.ClientStructureRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server → Client packet that syncs all registered structure IDs and structure
 * tag IDs so the editor UI can present searchable lists of what the server
 * recognizes.
 */
public class SyncStructureRegistryPacket {
    private final List<String> structureIds;
    private final List<String> structureTagIds;

    public SyncStructureRegistryPacket(List<String> structureIds, List<String> structureTagIds) {
        this.structureIds = structureIds;
        this.structureTagIds = structureTagIds;
    }

    public static void encode(SyncStructureRegistryPacket msg, FriendlyByteBuf buffer) {
        buffer.writeVarInt(msg.structureIds.size());
        for (String id : msg.structureIds) buffer.writeUtf(id);
        buffer.writeVarInt(msg.structureTagIds.size());
        for (String id : msg.structureTagIds) buffer.writeUtf(id);
    }

    public static SyncStructureRegistryPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<String> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) ids.add(buffer.readUtf());
        int tagSize = buffer.readVarInt();
        List<String> tags = new ArrayList<>(tagSize);
        for (int i = 0; i < tagSize; i++) tags.add(buffer.readUtf());
        return new SyncStructureRegistryPacket(ids, tags);
    }

    public static void handle(SyncStructureRegistryPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientStructureRegistry.set(msg.structureIds, msg.structureTagIds));
        ctx.get().setPacketHandled(true);
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
}
