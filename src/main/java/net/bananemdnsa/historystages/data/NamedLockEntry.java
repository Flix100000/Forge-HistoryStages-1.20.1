package net.bananemdnsa.historystages.data;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * A named entry (tag or mod ID) with an optional list of locked actions.
 * When lockActions is null, all actions are locked (default behaviour).
 * When lockActions is an empty list, no actions are locked.
 *
 * In JSON the field is written as {@code unlock_actions} (the complement — actions that are
 * NOT locked). {@code null} / no field means all actions are locked.
 */
public class NamedLockEntry implements net.bananemdnsa.historystages.data.display.TextOverrideHolder {

    /** Canonical ordered list of all recognised lock actions. */
    public static final List<String> ALL_ACTIONS = List.of(
            "equip", "attack", "place", "break", "pickup", "use", "loot", "recipe", "gui", "icon"
    );

    private final String id;
    private final List<String> lockActions; // null = all actions locked, empty = none locked

    // Per-entry REPLACE text overrides for the stage's hidden-display config.
    // null = no override → fall back to the stage default text.
    private final String nameTextOverride;
    private final String tooltipTextOverride;

    // Lazily computed — only meaningful when this entry represents an item tag.
    private transient TagKey<Item> cachedTagKey;

    public NamedLockEntry(String id) {
        this(id, null, null, null);
    }

    public NamedLockEntry(String id, List<String> lockActions) {
        this(id, lockActions, null, null);
    }

    public NamedLockEntry(String id, List<String> lockActions, String nameTextOverride, String tooltipTextOverride) {
        this.id = id;
        this.lockActions = (lockActions != null && !lockActions.isEmpty()) ? new ArrayList<>(lockActions) : null;
        this.nameTextOverride = (nameTextOverride != null && !nameTextOverride.isEmpty()) ? nameTextOverride : null;
        this.tooltipTextOverride = (tooltipTextOverride != null && !tooltipTextOverride.isEmpty()) ? tooltipTextOverride : null;
    }

    public String getId() { return id; }

    /** Returns null if all actions are locked, otherwise the explicit list of locked actions. */
    public List<String> getLockActions() { return lockActions; }

    public boolean hasLockActions() { return lockActions != null && !lockActions.isEmpty(); }

    @Override
    public String getNameTextOverride() { return nameTextOverride; }
    @Override
    public String getTooltipTextOverride() { return tooltipTextOverride; }

    public boolean hasNameTextOverride() { return nameTextOverride != null; }
    public boolean hasTooltipTextOverride() { return tooltipTextOverride != null; }

    /** Returns a cached TagKey for this entry's ID. Only call when this entry represents an item tag. */
    public TagKey<Item> getItemTagKey() {
        if (cachedTagKey == null) {
            cachedTagKey = TagKey.create(Registries.ITEM, new ResourceLocation(id));
        }
        return cachedTagKey;
    }

    public NamedLockEntry copy() {
        return new NamedLockEntry(id, lockActions != null ? new ArrayList<>(lockActions) : null,
                nameTextOverride, tooltipTextOverride);
    }
}
