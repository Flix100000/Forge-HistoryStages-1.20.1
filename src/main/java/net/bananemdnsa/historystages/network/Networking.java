package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.events.StructureLockEvents;
import net.bananemdnsa.historystages.util.IndividualStageData;
import net.bananemdnsa.historystages.util.StageData;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.List;

public final class Networking {
    private Networking() {
    }

    public static void registerCommon() {
        PayloadTypeRegistry.playS2C().register(StageDefinitionsPayload.TYPE, StageDefinitionsPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(UnlockedStagesPayload.TYPE, UnlockedStagesPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(UnlockedIndividualStagesPayload.TYPE, UnlockedIndividualStagesPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(StructureRegistryPayload.TYPE, StructureRegistryPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RequestStructureDebugPayload.TYPE, RequestStructureDebugPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(RequestStructureDebugPayload.TYPE,
                (payload, context) -> context.server().execute(() -> sendStructureDebug(context.player())));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncPlayer(handler.player));
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncPlayer(player);
        }
    }

    public static void syncPlayer(ServerPlayer player) {
        ServerPlayNetworking.send(player, new StageDefinitionsPayload(
                StageManager.serializeStages(StageManager.getStages()),
                StageManager.serializeStages(StageManager.getIndividualStages())
        ));
        ServerPlayNetworking.send(player, new UnlockedStagesPayload(
                StageData.get(player.serverLevel()).getUnlockedStages()
        ));
        ServerPlayNetworking.send(player, new UnlockedIndividualStagesPayload(
                new java.util.ArrayList<>(IndividualStageData.get(player.serverLevel()).getUnlockedStages(player.getUUID()))
        ));
        ServerPlayNetworking.send(player, createStructureRegistryPayload(player));
    }

    private static StructureRegistryPayload createStructureRegistryPayload(ServerPlayer player) {
        Registry<Structure> registry = player.serverLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<String> ids = registry.keySet().stream()
                .map(ResourceLocation::toString)
                .sorted(String::compareToIgnoreCase)
                .toList();
        List<String> tagIds = registry.getTagNames()
                .map(TagKey::location)
                .map(ResourceLocation::toString)
                .sorted(String::compareToIgnoreCase)
                .toList();
        return new StructureRegistryPayload(ids, tagIds);
    }

    private static void sendStructureDebug(ServerPlayer player) {
        if (!player.hasPermissions(2)) {
            return;
        }

        BlockPos pos = player.blockPosition();
        var holders = StructureLockEvents.collectStructureHoldersAt(player.serverLevel(), pos);

        player.sendSystemMessage(Component.literal("\u00A76--- Structures at \u00A7e"
                + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " \u00A76---"));

        if (holders.isEmpty()) {
            player.sendSystemMessage(Component.literal("  \u00A77(not inside any structure)"));
            return;
        }

        for (var holder : holders) {
            String id = holder.unwrapKey().map(key -> key.location().toString()).orElse("<unknown>");
            player.sendSystemMessage(Component.literal("  \u00A78\u2022 \u00A7f" + id));
            holder.tags().forEach(tag -> player.sendSystemMessage(
                    Component.literal("      \u00A78\u21B3 \u00A7b#" + tag.location())));
        }
    }
}
