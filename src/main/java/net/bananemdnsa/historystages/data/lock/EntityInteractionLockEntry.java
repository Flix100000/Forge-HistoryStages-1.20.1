package net.bananemdnsa.historystages.data.lock;

import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.NbtMatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * An interaction-locked entity entry with an optional list of locked interaction actions and an
 * optional held-item filter. When {@code lockActions} is null, all actions are locked (default
 * behaviour); when {@code lockItems} is null/empty, the lock applies regardless of the held item.
 *
 * <p>In JSON the action list is written as {@code unlock_actions} (the complement — actions that
 * are NOT locked); {@code null} / no field means all actions are locked. The item filter is
 * written as {@code lock_items} (a block list — the lock only bites while holding one of these).
 *
 * <p>The {@code other} action is a catch-all for any right-click interaction that does not map
 * to one of the specific gameplay actions.
 */
public class EntityInteractionLockEntry {

    /** Canonical ordered list of all recognised interaction actions. */
    public static final List<String> ALL_ACTIONS = List.of(
            "breed", "mount", "trade", "leash", "shear", "milk", "name", "equip", "other"
    );

    private final String id;
    private final List<String> lockActions; // null = all actions locked, empty = treated as all locked
    private final List<ItemEntry> lockItems; // null/empty = no item filter (lock applies to any held item)

    public EntityInteractionLockEntry(String id) {
        this(id, null, null);
    }

    public EntityInteractionLockEntry(String id, List<String> lockActions) {
        this(id, lockActions, null);
    }

    public EntityInteractionLockEntry(String id, List<String> lockActions, List<ItemEntry> lockItems) {
        this.id = id;
        this.lockActions = (lockActions != null && !lockActions.isEmpty()) ? new ArrayList<>(lockActions) : null;
        this.lockItems = (lockItems != null && !lockItems.isEmpty()) ? new ArrayList<>(lockItems) : null;
    }

    public String getId() { return id; }

    /** Returns null if all actions are locked, otherwise the explicit list of locked actions. */
    public List<String> getLockActions() { return lockActions; }

    public boolean hasLockActions() { return lockActions != null && !lockActions.isEmpty(); }

    /** Returns null if there is no item filter, otherwise the held items this lock is limited to. */
    public List<ItemEntry> getLockItems() { return lockItems; }

    public boolean hasLockItems() { return lockItems != null && !lockItems.isEmpty(); }

    /** True if the given action is blocked by this entry. Null filter blocks every action. */
    public boolean blocksAction(String action) {
        return lockActions == null || lockActions.contains(action);
    }

    /**
     * True if the held stack is covered by this entry's item filter.
     * No filter → always true (the lock applies to any hand, empty included).
     * With a filter → an empty hand never matches, since there is no item to match against.
     */
    public boolean matchesItem(ItemStack held) {
        if (!hasLockItems()) return true;
        if (held == null || held.isEmpty()) return false;
        for (ItemEntry entry : lockItems) {
            if (itemEntryMatches(entry, held)) return true;
        }
        return false;
    }

    /** Matches a filter entry against a stack: "#tag" entries match by item tag, others by item ID. */
    private static boolean itemEntryMatches(ItemEntry entry, ItemStack held) {
        String entryId = entry.getId();
        if (entryId == null || entryId.isBlank()) return false;

        if (entryId.startsWith("#")) {
            ResourceLocation tagRl = ResourceLocation.tryParse(entryId.substring(1));
            if (tagRl == null) return false;
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagRl);
            if (!held.is(tagKey)) return false;
        } else {
            ResourceLocation heldRl = ForgeRegistries.ITEMS.getKey(held.getItem());
            if (heldRl == null || !heldRl.toString().equals(entryId)) return false;
        }

        return !entry.hasNbt() || NbtMatcher.matches(held, entry.getNbt());
    }

    public EntityInteractionLockEntry copy() {
        List<ItemEntry> itemsCopy = null;
        if (lockItems != null) {
            itemsCopy = new ArrayList<>(lockItems.size());
            for (ItemEntry e : lockItems) itemsCopy.add(e.copy());
        }
        return new EntityInteractionLockEntry(
                id,
                lockActions != null ? new ArrayList<>(lockActions) : null,
                itemsCopy);
    }
}
