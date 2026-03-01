package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.util.ClientStageCache;
import net.bananemdnsa.historystages.jei.JEIPlugin; // Import für JEI Refresh
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
                        // --- DER HARD-RESET (Wichtig für neue Rezepte) ---
                        if (mc.getConnection() != null && mc.getConnection().getRecipeManager() != null) {
                            // Wir leeren die Rezepte kurzzeitig, um den Re-Index zu erzwingen
                            mc.getConnection().getRecipeManager().replaceRecipes(java.util.Collections.emptyList());
                        }

                        // --- MOD-SPEZIFISCHE UPDATES ---
                        // Wir rufen diese jetzt über die sichere Hilfsklasse auf
                        if (net.minecraftforge.fml.ModList.get().isLoaded("jei")) {
                            ExternalMods.refreshJEI();
                        }

                        if (net.minecraftforge.fml.ModList.get().isLoaded("emi")) {
                            ExternalMods.refreshEMI();
                        }

                        System.out.println("[HistoryStages] Hard-Reset & Mod-Sync completed.");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static class ExternalMods {
        private static void refreshJEI() {
            try {
                // Dein bestehender JEI-Refresh
                net.bananemdnsa.historystages.jei.JEIPlugin.refreshJei();
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