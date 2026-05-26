package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.block.entity.ResearchPedestalBlockEntity;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.DependencyChecker;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.bananemdnsa.historystages.events.StructureLockEvents;
import net.bananemdnsa.historystages.util.IndividualStageData;
import net.bananemdnsa.historystages.util.StageData;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
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
        PayloadTypeRegistry.playS2C().register(SyncDependencyStatusPayload.TYPE, SyncDependencyStatusPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SyncConfigPayload.TYPE, SyncConfigPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(StageUnlockedToastPayload.TYPE, StageUnlockedToastPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(LockFeedbackPayload.TYPE, LockFeedbackPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RequestStructureDebugPayload.TYPE, RequestStructureDebugPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SaveStagePacket.TYPE, SaveStagePacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(DeleteStagePacket.TYPE, DeleteStagePacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ToggleStageLockPacket.TYPE, ToggleStageLockPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SaveConfigPacket.TYPE, SaveConfigPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(CheckDependencyPacket.TYPE, CheckDependencyPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(DepositDependencyPacket.TYPE, DepositDependencyPacket.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(RequestStructureDebugPayload.TYPE,
                (payload, context) -> context.server().execute(() -> sendStructureDebug(context.player())));

        ServerPlayNetworking.registerGlobalReceiver(SaveStagePacket.TYPE,
                (payload, context) -> context.server().execute(() -> handleSaveStage(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(DeleteStagePacket.TYPE,
                (payload, context) -> context.server().execute(() -> handleDeleteStage(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ToggleStageLockPacket.TYPE,
                (payload, context) -> context.server().execute(() -> handleToggleStageLock(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(SaveConfigPacket.TYPE,
                (payload, context) -> context.server().execute(() -> handleSaveConfig(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(CheckDependencyPacket.TYPE,
                (payload, context) -> context.server().execute(() -> handleCheckDependency(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(DepositDependencyPacket.TYPE,
                (payload, context) -> context.server().execute(() -> handleDepositDependency(context.player(), payload)));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncPlayer(handler.player));
    }

    private static boolean requireOperator(ServerPlayer player) {
        if (player.hasPermissions(2)) {
            return true;
        }
        HistoryStages.LOGGER.warn("Rejecting editor packet from non-op player {}", player.getGameProfile().getName());
        return false;
    }

    private static void handleSaveStage(ServerPlayer player, SaveStagePacket payload) {
        if (!requireOperator(player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        StageEntry entry;
        try {
            entry = StageEntry.fromJson(payload.entryJson());
        } catch (Exception e) {
            HistoryStages.LOGGER.warn("Failed to parse SaveStagePacket entry JSON: {}", e.getMessage());
            return;
        }
        boolean existed = payload.individual()
                ? StageManager.getIndividualStages().containsKey(payload.stageId())
                : StageManager.getStages().containsKey(payload.stageId());
        boolean success = StageManager.saveStage(payload.stageId(), entry, payload.individual());
        StageManager.reloadStages();
        syncAll(server);
        if (success) {
            String prefix = payload.individual() ? "Individual stage" : "Stage";
            String action = existed ? "saved" : "created";
            player.sendSystemMessage(successMessage(prefix + " '" + payload.stageId() + "' " + action + " successfully."));
        }
    }

    private static void handleDeleteStage(ServerPlayer player, DeleteStagePacket payload) {
        if (!requireOperator(player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        boolean success = StageManager.deleteStage(payload.stageId(), payload.individual());
        StageManager.reloadStages();
        syncAll(server);
        if (success) {
            String prefix = payload.individual() ? "Individual stage" : "Stage";
            player.sendSystemMessage(successMessage(prefix + " '" + payload.stageId() + "' deleted successfully."));
        }
    }

    private static void handleToggleStageLock(ServerPlayer player, ToggleStageLockPacket payload) {
        if (!requireOperator(player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        server.getAllLevels().forEach(level -> {
            if (payload.unlocked()) {
                StageData.get(level).addStage(payload.stageId());
            } else {
                StageData.get(level).removeStage(payload.stageId());
            }
        });
        syncAll(server);
    }

    private static void handleSaveConfig(ServerPlayer player, SaveConfigPacket payload) {
        if (!requireOperator(player)) return;
        Config.applyEditorValues(payload.clientValues(), payload.commonValues());
        Config.save();
    }

    private static void handleCheckDependency(ServerPlayer player, CheckDependencyPacket payload) {
        StageEntry entry = payload.individual()
                ? StageManager.getIndividualStages().get(payload.stageId())
                : StageManager.getStages().get(payload.stageId());
        if (entry == null) {
            return;
        }
        CompoundTag deposited = null;
        BlockEntity blockEntity = player.level().getBlockEntity(payload.blockPos());
        if (blockEntity instanceof ResearchPedestalBlockEntity pedestal) {
            CompoundTag scrollTag = pedestal.getScrollStack()
                    .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (scrollTag.contains("DepositedDependencies")) {
                deposited = scrollTag.getCompound("DepositedDependencies");
            }
        }
        DependencyResult result = DependencyChecker.checkAll(entry, player, player.level(), deposited);
        ServerPlayNetworking.send(player, SyncDependencyStatusPayload.of(payload.stageId(), result));
    }

    private static void handleDepositDependency(ServerPlayer player, DepositDependencyPacket payload) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        BlockEntity blockEntity = player.level().getBlockEntity(payload.blockPos());
        if (blockEntity instanceof ResearchPedestalBlockEntity pedestal) {
            pedestal.handleDependencyDeposit(player, payload.groupIndex(), payload.depType(), payload.data());
            syncAll(server);
        }
    }

    private static Component successMessage(String text) {
        return Component.literal("[HistoryStages] ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(text).withStyle(ChatFormatting.GREEN));
    }

    public static void sendStageUnlockedToast(ServerPlayer player, String stageName, String iconId) {
        if (!Config.COMMON.individualUseToasts) return;
        ServerPlayNetworking.send(player, new StageUnlockedToastPayload(stageName, iconId == null ? "" : iconId));
    }

    public static void sendStageUnlockedToastToAll(MinecraftServer server, String stageName, String iconId) {
        if (!Config.COMMON.useToasts) return;
        StageUnlockedToastPayload payload = new StageUnlockedToastPayload(stageName, iconId == null ? "" : iconId);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncPlayer(player);
        }
    }

    public static void syncPlayer(ServerPlayer player) {
        ServerPlayNetworking.send(player, new SyncConfigPayload(Config.snapshotCommon()));
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
