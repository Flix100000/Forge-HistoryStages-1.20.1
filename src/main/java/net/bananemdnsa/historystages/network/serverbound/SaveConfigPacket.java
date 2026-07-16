package net.bananemdnsa.historystages.network.serverbound;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.SyncConfigPacket;
import net.bananemdnsa.historystages.network.clientbound.EditorFeedbackPacket;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record SaveConfigPacket(Map<String, String> configValues, boolean isClient) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SaveConfigPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "save_config"));

    public static final StreamCodec<FriendlyByteBuf, SaveConfigPacket> STREAM_CODEC =
            StreamCodec.of(SaveConfigPacket::encode, SaveConfigPacket::decode);

    private static void encode(FriendlyByteBuf buffer, SaveConfigPacket msg) {
        buffer.writeBoolean(msg.isClient);
        buffer.writeInt(msg.configValues.size());
        for (Map.Entry<String, String> entry : msg.configValues.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeUtf(entry.getValue());
        }
    }

    private static SaveConfigPacket decode(FriendlyByteBuf buffer) {
        boolean isClient = buffer.readBoolean();
        int size = buffer.readInt();
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < size; i++) {
            values.put(buffer.readUtf(), buffer.readUtf());
        }
        return new SaveConfigPacket(values, isClient);
    }

    public static void handle(SaveConfigPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            if (!msg.isClient) {
                applyCommonConfig(msg.configValues);
                Config.COMMON_SPEC.save();
                PacketHandler.sendConfigToAll(SyncConfigPacket.fromServerConfig());
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.success(
                                "editor.historystages.toast.config_saved.title",
                                "editor.historystages.toast.config_saved.message"),
                        player);
            }
        });
    }

    public static void applyCommonConfig(Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            switch (key) {
                case "showWelcomeMessage" -> Config.COMMON.showWelcomeMessage.set(Boolean.parseBoolean(value));
                case "showDebugErrors" -> Config.COMMON.showDebugErrors.set(Boolean.parseBoolean(value));
                case "enableRuntimeLogging" -> Config.COMMON.enableRuntimeLogging.set(Boolean.parseBoolean(value));
                case "lockMobLoot" -> Config.COMMON.lockMobLoot.set(Boolean.parseBoolean(value));
                case "lockBlockBreaking" -> Config.COMMON.lockBlockBreaking.set(Boolean.parseBoolean(value));
                case "lockedBlockBreakSpeedMultiplier" -> {
                    try { Config.COMMON.lockedBlockBreakSpeedMultiplier.set(Double.parseDouble(value)); } catch (NumberFormatException ignored) {}
                }
                case "lockItemUsage" -> Config.COMMON.lockItemUsage.set(Boolean.parseBoolean(value));
                case "lockEntityItems" -> Config.COMMON.lockEntityItems.set(Boolean.parseBoolean(value));
                case "broadcastChat" -> Config.COMMON.broadcastChat.set(Boolean.parseBoolean(value));
                case "unlockMessageFormat" -> Config.COMMON.unlockMessageFormat.set(value);
                case "useActionbar" -> Config.COMMON.useActionbar.set(Boolean.parseBoolean(value));
                case "useSounds" -> Config.COMMON.useSounds.set(Boolean.parseBoolean(value));
                case "useToasts" -> Config.COMMON.useToasts.set(Boolean.parseBoolean(value));
                case "defaultStageIcon" -> Config.COMMON.defaultStageIcon.set(value);
                case "researchTimeInSeconds" -> {
                    try { Config.COMMON.researchTimeInSeconds.set(Integer.parseInt(value)); } catch (NumberFormatException ignored) {}
                }
                case "researchBoosters" -> {
                    List<String> boosterList = Arrays.stream(value.split(";"))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                    Config.COMMON.researchBoosters.set(boosterList);
                    // Rebuild the in-memory registry so changes apply immediately.
                    net.bananemdnsa.historystages.research.ResearchBoosterRegistry.rebuildFromConfig(boosterList);
                }
                case "showDependencyScreenInPedestal" -> Config.COMMON.showDependencyScreenInPedestal.set(Boolean.parseBoolean(value));
                case "lockScrollWhileResearching" -> Config.COMMON.lockScrollWhileResearching.set(Boolean.parseBoolean(value));
                case "useReplacements" -> Config.COMMON.useReplacements.set(Boolean.parseBoolean(value));
                case "replacementItems" -> {
                    List<String> itemList = Arrays.stream(value.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                    Config.COMMON.replacementItems.set(itemList);
                }
                case "replacementTags" -> {
                    List<String> tagList = Arrays.stream(value.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                    Config.COMMON.replacementTags.set(tagList);
                }
                case "structureCheckInterval" -> {
                    try { Config.COMMON.structureCheckInterval.set(Integer.parseInt(value)); } catch (NumberFormatException ignored) {}
                }
                case "structureDamageEnabled" -> Config.COMMON.structureDamageEnabled.set(Boolean.parseBoolean(value));
                case "structureDamageAmount" -> {
                    try { Config.COMMON.structureDamageAmount.set(Double.parseDouble(value)); } catch (NumberFormatException ignored) {}
                }
                case "structureDamageInterval" -> {
                    try { Config.COMMON.structureDamageInterval.set(Integer.parseInt(value)); } catch (NumberFormatException ignored) {}
                }
                case "structureMessageEnabled" -> Config.COMMON.structureMessageEnabled.set(Boolean.parseBoolean(value));
                case "structureLockMessageFormat" -> Config.COMMON.structureLockMessageFormat.set(value);
                case "structureLockInChat" -> Config.COMMON.structureLockInChat.set(Boolean.parseBoolean(value));
                case "structureBlockRightClick" -> Config.COMMON.structureBlockRightClick.set(Boolean.parseBoolean(value));
                case "structureBlockLeftClick" -> Config.COMMON.structureBlockLeftClick.set(Boolean.parseBoolean(value));
                case "structureBlockProjectiles" -> Config.COMMON.structureBlockProjectiles.set(Boolean.parseBoolean(value));
                case "msgDimensionUnknown" -> Config.COMMON.msgDimensionUnknown.set(value);
                case "msgMobUnknown" -> Config.COMMON.msgMobUnknown.set(value);
                case "msgItemLocked" -> Config.COMMON.msgItemLocked.set(value);
                case "msgBlockLocked" -> Config.COMMON.msgBlockLocked.set(value);
                case "msgEntityItemLocked" -> Config.COMMON.msgEntityItemLocked.set(value);
                case "msgEnchantmentLocked" -> Config.COMMON.msgEnchantmentLocked.set(value);

                // Individual stage settings. These are offered by the config editor and read
                // throughout the mod, but had no case here, so changing them in the GUI did
                // nothing — the switch dropped them silently.
                case "individualLockItemPickup" -> Config.COMMON.individualLockItemPickup.set(Boolean.parseBoolean(value));
                case "individualDropOnRevoke" -> Config.COMMON.individualDropOnRevoke.set(Boolean.parseBoolean(value));
                case "individualLockBlockBreaking" -> Config.COMMON.individualLockBlockBreaking.set(Boolean.parseBoolean(value));
                case "individualLockedBlockBreakSpeedMultiplier" ->
                        Config.COMMON.individualLockedBlockBreakSpeedMultiplier.set(Double.parseDouble(value));
                case "individualLockItemUsage" -> Config.COMMON.individualLockItemUsage.set(Boolean.parseBoolean(value));
                case "individualLockBlockInteraction" -> Config.COMMON.individualLockBlockInteraction.set(Boolean.parseBoolean(value));
                case "individualBroadcastChat" -> Config.COMMON.individualBroadcastChat.set(Boolean.parseBoolean(value));
                case "individualUnlockMessageFormat" -> Config.COMMON.individualUnlockMessageFormat.set(value);
                case "individualUseActionbar" -> Config.COMMON.individualUseActionbar.set(Boolean.parseBoolean(value));
                case "individualUseSounds" -> Config.COMMON.individualUseSounds.set(Boolean.parseBoolean(value));
                case "individualUseToasts" -> Config.COMMON.individualUseToasts.set(Boolean.parseBoolean(value));

                // Same omission, outside the individual section.
                case "lockBlockInteraction" -> Config.COMMON.lockBlockInteraction.set(Boolean.parseBoolean(value));
            }
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
