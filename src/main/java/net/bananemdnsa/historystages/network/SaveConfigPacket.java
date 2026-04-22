package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
                player.sendSystemMessage(Component.literal("§7[HistoryStages] §aCommon config saved."));
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
                case "researchTimeInSeconds" -> {
                    try { Config.COMMON.researchTimeInSeconds.set(Integer.parseInt(value)); } catch (NumberFormatException ignored) {}
                }
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
            }
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
