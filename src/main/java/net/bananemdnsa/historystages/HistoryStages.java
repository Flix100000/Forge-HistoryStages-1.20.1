package net.bananemdnsa.historystages;

import net.minecraft.resources.ResourceLocation;

public final class HistoryStages {
    public static final String MOD_ID = "historystages";

    private HistoryStages() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
