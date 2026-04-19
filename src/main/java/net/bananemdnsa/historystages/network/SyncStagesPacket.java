package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.jei.JEIPlugin;
import net.bananemdnsa.historystages.util.ClientStageCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SyncStagesPacket {
    private final List<String> unlockedStages;

    public SyncStagesPacket(List<String> unlockedStages) {
        this.unlockedStages = unlockedStages;
    }

    // Wandelt die Liste in Daten um, die durch das Internet passen (Senden)
    public static void encode(SyncStagesPacket msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.unlockedStages.size());
        for (String stage : msg.unlockedStages) {
            buffer.writeUtf(stage);
        }
    }

    // Wandelt die Daten wieder in eine Liste um (Empfangen)
    public static SyncStagesPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readInt();
        List<String> stages = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            stages.add(buffer.readUtf());
        }
        return new SyncStagesPacket(stages);
    }

    public static void handle(SyncStagesPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 1. Client-Speicher aktualisieren
            ClientStageCache.setUnlockedStages(msg.unlockedStages);

            if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                mc.execute(() -> {
                    try {
                        // Grafik-Refresh für Lock-Overlays
                        if (mc.levelRenderer != null) {
                            mc.levelRenderer.allChanged();
                        }

                        // JEI: Item-Sichtbarkeit aktualisieren (Decorator für Rezepte prüft live)
                        if (net.minecraftforge.fml.ModList.get().isLoaded("jei")) {
                            JEIPlugin.refreshJei();
                        }

                        // EMI extra reload (hat eigenen Reload-Mechanismus)
                        if (net.minecraftforge.fml.ModList.get().isLoaded("emi")) {
                            ExternalMods.refreshEMI();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static class ExternalMods {
        private static void refreshEMI() {
            try {
                Class<?> reloadManager = Class.forName("dev.emi.emi.runtime.EmiReloadManager");
                reloadManager.getMethod("reload").invoke(null);
            } catch (Throwable ignored) {}
        }
    }
}