package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.util.ClientStageCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
            Set<String> oldStages = ClientStageCache.snapshot();

            // 1. Client-Speicher aktualisieren
            ClientStageCache.setUnlockedStages(msg.unlockedStages);
            Set<String> newStages = ClientStageCache.snapshot();
            Set<String> changedStages = new HashSet<>(oldStages);
            changedStages.addAll(newStages);
            Set<String> intersection = new HashSet<>(oldStages);
            intersection.retainAll(newStages);
            changedStages.removeAll(intersection);

            if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                mc.execute(() -> {
                    try {
                        if (net.minecraftforge.fml.ModList.get().isLoaded("jei")) {
                            ExternalMods.refreshJEI(changedStages);
                        }

                        if (net.minecraftforge.fml.ModList.get().isLoaded("emi")) {
                            ExternalMods.refreshEMI();
                        }

                        System.out.println("[HistoryStages] Stage sync completed.");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static class ExternalMods {
        private static void refreshJEI(Set<String> changedStages) {
            try {
                net.bananemdnsa.historystages.jei.JEIPlugin.refreshJeiForStageChanges(changedStages);
            } catch (Throwable ignored) {}
        }

        private static void refreshEMI() {
            try {
                // "Leiser" Refresh für EMI: Suchtext triggert Re-Filter der neuen Rezepte
                String currentSearch = dev.emi.emi.api.EmiApi.getSearchText();
                dev.emi.emi.api.EmiApi.setSearchText(currentSearch);

                // Optional: Falls EMI trotzdem nicht alle neuen Rezepte sieht,
                // kann man hier noch dev.emi.emi.api.EmiApi.forceReload() per Reflection einbauen.
            } catch (Throwable ignored) {}
        }
    }
}
