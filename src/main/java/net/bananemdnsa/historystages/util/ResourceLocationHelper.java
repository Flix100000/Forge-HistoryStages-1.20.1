package net.bananemdnsa.historystages.util;

import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.resources.ResourceLocation;

public class ResourceLocationHelper {

    public static ResourceLocation MOD_RESOURCE_LOCATION(String pathName) {
        return RESOURCE_LOCATION(HistoryStages.MOD_ID, pathName);
    }

    public static ResourceLocation RESOURCE_LOCATION(String namespace, String pathName) {
        return ResourceLocation.fromNamespaceAndPath(namespace, pathName);
    }
}
