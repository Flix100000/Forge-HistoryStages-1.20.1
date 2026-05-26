package net.bananemdnsa.historystages;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HistoryStages {
    public static final String MOD_ID = "historystages";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private HistoryStages() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
