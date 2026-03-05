package net.bananemdnsa.historystages.client;

import net.minecraft.client.Minecraft;

public class ClientToastHandler {
    public static void showToast(String stageName) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.getToasts().addToast(new StageUnlockedToast(stageName)));
    }
}
