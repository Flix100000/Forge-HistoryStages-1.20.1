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

    // Was passiert, wenn das Paket ankommt?
    public static void handle(SyncStagesPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 1. Wir speichern die Info im Client-Speicher
            ClientStageCache.setUnlockedStages(msg.unlockedStages);

            // 2. Wir sagen JEI, dass es die Items neu prüfen soll
            try {
                // Wir rufen die statische Methode in deinem JEIPlugin auf
                JEIPlugin.refreshJei();
            } catch (NoClassDefFoundError | Exception e) {
                // Falls JEI nicht installiert ist, ignorieren wir den Fehler einfach
            }
        });
        ctx.get().setPacketHandled(true);
    }
}