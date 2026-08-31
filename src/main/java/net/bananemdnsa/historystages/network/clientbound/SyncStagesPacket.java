package net.bananemdnsa.historystages.network.clientbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.cache.ClientStageCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record SyncStagesPacket(List<String> unlockedStages) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncStagesPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "sync_stages"));

    public static final StreamCodec<FriendlyByteBuf, SyncStagesPacket> STREAM_CODEC =
            StreamCodec.of(SyncStagesPacket::encode, SyncStagesPacket::decode);

    private static void encode(FriendlyByteBuf buffer, SyncStagesPacket msg) {
        buffer.writeInt(msg.unlockedStages.size());
        for (String stage : msg.unlockedStages) {
            buffer.writeUtf(stage);
        }
    }

    private static SyncStagesPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readInt();
        List<String> stages = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            stages.add(buffer.readUtf());
        }
        return new SyncStagesPacket(stages);
    }

    public static void handle(SyncStagesPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientStageCache.setUnlockedStages(msg.unlockedStages);

            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            mc.execute(() -> {
                try {
                    if (mc.levelRenderer != null) {
                        mc.levelRenderer.allChanged();
                    }

                    // Same for the vanilla recipe book. The global path also triggers a recipe
                    // resync, which rebuilds it too, but doing it here keeps the book correct
                    // without depending on that.
                    net.bananemdnsa.historystages.client.ClientRecipeBookRefresh.rebuild();

                    if (net.neoforged.fml.ModList.get().isLoaded("emi")) {
                        ExternalMods.refreshEMI();
                    }

                    // JEI hiding (Issue #64): refresh visibility after stage cache updated.
                    // Null-safe — no-op if JEI is not installed.
                    if (net.neoforged.fml.ModList.get().isLoaded("jei")) {
                        try {
                            net.bananemdnsa.historystages.compat.jei.JEIPlugin.tryApplyDiff();
                        } catch (Throwable ignored) {}
                    }

                    System.out.println("[HistoryStages] Hard-Reset & Mod-Sync completed.");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static class ExternalMods {
        private static void refreshEMI() {
            try {
                String currentSearch = dev.emi.emi.api.EmiApi.getSearchText();
                dev.emi.emi.api.EmiApi.setSearchText(currentSearch);
            } catch (Throwable ignored) {}
        }
    }
}
