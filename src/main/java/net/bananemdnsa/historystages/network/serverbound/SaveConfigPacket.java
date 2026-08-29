package net.bananemdnsa.historystages.network.serverbound;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.SyncConfigPacket;
import net.bananemdnsa.historystages.network.clientbound.EditorFeedbackPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncVisualConfigPacket;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.config.AddonConfigSections;
import net.bananemdnsa.historystages.data.config.ConfigSpecCodec;
import net.bananemdnsa.historystages.data.tooltip.ScrollTooltipLayout;
import net.bananemdnsa.historystages.research.ResearchBoosterRegistry;
import net.bananemdnsa.historystages.util.lock.BiomeEffectRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

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

            if (msg.isClient) {
                // The visual settings are server-owned now, so this arrives instead of the editor
                // writing them into its own client and nowhere else.
                ConfigSpecCodec.apply(
                        Config.VISUAL_SPEC, msg.configValues, true, ConfigSpecCodec.NO_EXTRA_CHECK);
                Config.VISUAL_SPEC.save();

                // Everyone, including the sender: the sender's own spec is only updated by the
                // sync path, so it must not be skipped here.
                PacketHandler.sendVisualConfigToAll(SyncVisualConfigPacket.fromServerConfig());
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.success(
                                "editor.historystages.toast.visual_config_saved.title",
                                "editor.historystages.toast.visual_config_saved.message"),
                        player);
            } else {
                applyCommonConfig(msg.configValues);
                Config.GAMEPLAY_SPEC.save();
                PacketHandler.sendConfigToAll(SyncConfigPacket.fromServerConfig());
                PacketHandler.sendEditorFeedback(
                        EditorFeedbackPacket.success(
                                "editor.historystages.toast.config_saved.title",
                                "editor.historystages.toast.config_saved.message"),
                        player);
            }
        });
    }

    /**
     * Applies wire values to the common config. Runs on the server when an admin saves the editor,
     * and on the client when the server syncs back.
     *
     * <p>The values are addressed by dotted toml path and written by walking the spec. The two
     * hand-maintained key lists this replaced kept drifting apart — at one point 28 keys the
     * editor could change were never sent to any client.
     *
     * <p>Addon values are not in the spec and are applied separately: an addon holds its own state
     * behind the write callback it registered, so there is nothing in {@code GAMEPLAY_SPEC} for the
     * walk to find. Their wire keys come from {@link AddonConfigSections}, which mints them in one
     * place precisely so collect and apply cannot disagree about what a value is called.
     */
    public static void applyCommonConfig(Map<String, String> values) {
        ConfigSpecCodec.apply(Config.GAMEPLAY_SPEC, values, true, ConfigSpecCodec.NO_EXTRA_CHECK);

        for (AddonConfigSections.CommonEntry entry : AddonConfigSections.commonEntries()) {
            String incoming = values.get(entry.wireKey());
            if (incoming != null) entry.write().accept(incoming);
        }

        // Rebuilt unconditionally rather than only when their own key arrived. These three parse a
        // config list into an in-memory registry, and a rebuild is cheap; a key-to-rebuild mapping
        // would be one more hand-written table of exactly the kind this refactor removed, and the
        // failure it would hide — a list that changed but kept behaving like the old one until the
        // next restart — is invisible until someone reports it as a ghost.
        ResearchBoosterRegistry.rebuildFromConfig(Config.GAMEPLAY.researchBoosters.get());
        BiomeEffectRegistry.rebuildFromConfig(Config.GAMEPLAY.biomeEffects.get());
        ScrollTooltipLayout.rebuildFromConfig(Config.GAMEPLAY.scrollTooltipLines.get());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
