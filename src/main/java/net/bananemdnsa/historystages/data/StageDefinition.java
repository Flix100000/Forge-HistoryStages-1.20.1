package net.bananemdnsa.historystages.data;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

public class StageDefinition {

    private final String stageName;
    private final StageScope _scope;

    public StageDefinition(String stage, StageScope scope) {
        stageName = stage;
        _scope = scope;
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

    public StageScope getStageScope() {
        return _scope;
    }
}
