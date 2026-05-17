package net.bananemdnsa.historystages.data;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

public class NamedLockEntry {
    public static final List<String> ALL_ACTIONS = List.of(
            "equip", "attack", "place", "break", "pickup", "use", "loot", "recipe", "gui", "icon"
    );

    private final String id;
    private final List<String> lockActions;
    private transient TagKey<Item> cachedTagKey;

    public NamedLockEntry(String id) {
        this.id = id;
        this.lockActions = null;
    }

    public NamedLockEntry(String id, List<String> lockActions) {
        this.id = id;
        this.lockActions = lockActions != null ? new ArrayList<>(lockActions) : null;
    }

    public String getId() {
        return id;
    }

    public List<String> getLockActions() {
        return lockActions;
    }

    public boolean hasLockActions() {
        return lockActions != null;
    }

    public TagKey<Item> getItemTagKey() {
        if (cachedTagKey == null) {
            cachedTagKey = TagKey.create(Registries.ITEM, ResourceLocation.parse(id));
        }
        return cachedTagKey;
    }

    public NamedLockEntry copy() {
        return new NamedLockEntry(id, lockActions != null ? new ArrayList<>(lockActions) : null);
    }
}
