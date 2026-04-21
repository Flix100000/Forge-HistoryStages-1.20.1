package net.bananemdnsa.historystages.data;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

public class StageDefinition {

    private final String stageName;

    public StageDefinition(String stage) {
        stageName = stage;
    }

    public String getName() {
        return stageName;
    }

    public List<Item> getLockedItems() {
        return new ArrayList<>();
    }

    public List<ResourceLocation> getLockedMods() {
        return new ArrayList<>();
    }
}
