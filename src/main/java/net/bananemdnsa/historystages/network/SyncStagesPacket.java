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

            // 2. JEI & Client-Reload-Trigger
            if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                mc.execute(() -> {
                    try {
                        // Das hier triggert den JEI-Neuaufbau (wie bei /history reload)
                        if (mc.getConnection() != null) {
                            // Simuliert den Empfang neuer Rezepte, was JEI zum kompletten Neustart zwingt
                            mc.getConnection().getRecipeManager().replaceRecipes(java.util.Collections.emptyList());
                        }

                        // Der eigentliche JEI Refresh
                        JEIPlugin.refreshJei();

                        System.out.println("[HistoryStages] Client Sync & JEI Hard-Refresh triggered.");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}