package net.bananemdnsa.historystages.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Method;

public final class OptionalRecipeViewHooks {
    private OptionalRecipeViewHooks() {
    }

    public static void refreshAll() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        if (!minecraft.isSameThread()) {
            minecraft.execute(OptionalRecipeViewHooks::refreshAll);
            return;
        }

        refreshJei();
        refreshEmi();
    }

    private static void refreshJei() {
        if (!FabricLoader.getInstance().isModLoaded("jei")) {
            return;
        }

        try {
            Class<?> hookClass = Class.forName("net.bananemdnsa.historystages.jei.JeiVisibilityManager");
            Method refresh = hookClass.getMethod("refresh");
            refresh.invoke(null);
        } catch (Throwable ignored) {
        }
    }

    private static void refreshEmi() {
        if (!FabricLoader.getInstance().isModLoaded("emi")) {
            return;
        }

        try {
            Class<?> reloadManager = Class.forName("dev.emi.emi.runtime.EmiReloadManager");
            Method reload = reloadManager.getMethod("reload");
            reload.invoke(null);
        } catch (NoSuchMethodException ignored) {
            try {
                Class<?> reloadManager = Class.forName("dev.emi.emi.runtime.EmiReloadManager");
                Method method = reloadManager.getMethod("scheduleReload");
                method.invoke(null);
            } catch (Throwable ignoredAgain) {
            }
        } catch (Throwable ignored) {
        }
    }
}
