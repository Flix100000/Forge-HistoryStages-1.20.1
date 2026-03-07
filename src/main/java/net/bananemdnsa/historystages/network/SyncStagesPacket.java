package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.util.ClientStageCache;
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

                    if (mc.getConnection() != null && mc.getConnection().getRecipeManager() != null) {
                        mc.getConnection().getRecipeManager().replaceRecipes(java.util.Collections.emptyList());
                    }

                    if (net.neoforged.fml.ModList.get().isLoaded("jei")) {
                        ExternalMods.refreshJEI();
                    }

                    if (net.neoforged.fml.ModList.get().isLoaded("emi")) {
                        ExternalMods.refreshEMI();
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
        private static void refreshJEI() {
            try {
                net.bananemdnsa.historystages.jei.JEIPlugin.refreshJei();
            } catch (Throwable ignored) {}
        }

        private static void refreshEMI() {
            try {
                String currentSearch = dev.emi.emi.api.EmiApi.getSearchText();
                dev.emi.emi.api.EmiApi.setSearchText(currentSearch);
            } catch (Throwable ignored) {}
        }
    }
}
