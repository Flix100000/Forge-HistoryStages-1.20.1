package net.bananemdnsa.historystages.client.editor.widget;

import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.NamedLockEntry;
import net.bananemdnsa.historystages.data.StageEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Builds per-trigger-type "is this id locked by this stage" predicates used by the
 * auto-trigger editor to filter out entries from the Searchable widgets. Resolves both
 * direct locks (item id in {@link StageEntry#getItemEntries()}, dimension id in
 * {@link StageEntry#getDimensions()}, etc.) and indirect locks (mod-namespace match
 * minus exceptions, tag membership).
 */
public final class StageLockFilter {

    private StageLockFilter() {}

    /** Item-id predicate: covers direct items, locked mods (minus exceptions), and locked item tags. */
    public static Predicate<String> forItems(StageEntry entry) {
        Set<String> directItems = new HashSet<>();
        for (ItemEntry e : entry.getItemEntries()) directItems.add(e.getId());

        Set<String> exceptionItems = new HashSet<>();
        for (ItemEntry e : entry.getModExceptionEntries()) exceptionItems.add(e.getId());

        Set<String> lockedMods = new HashSet<>();
        for (NamedLockEntry m : entry.getModEntries()) lockedMods.add(m.getId());

        Set<String> taggedItems = new HashSet<>();
        for (String tagId : entry.getTags()) {
            ResourceLocation tagRl = ResourceLocation.tryParse(tagId);
            if (tagRl == null) continue;
            TagKey<Item> key = TagKey.create(Registries.ITEM, tagRl);
            BuiltInRegistries.ITEM.getTag(key).ifPresent(holders -> holders.forEach(h ->
                    h.unwrapKey().ifPresent(rk -> taggedItems.add(rk.location().toString()))));
        }

        return id -> {
            if (id == null) return false;
            if (directItems.contains(id)) return true;
            if (taggedItems.contains(id)) return true;
            int colon = id.indexOf(':');
            if (colon > 0) {
                String ns = id.substring(0, colon);
                if (lockedMods.contains(ns) && !exceptionItems.contains(id)) return true;
            }
            return false;
        };
    }

    /** Entity-id predicate: attack-lock + spawn-lock entries. */
    public static Predicate<String> forEntities(StageEntry entry) {
        Set<String> locked = new HashSet<>();
        if (entry.getEntities() != null) {
            locked.addAll(entry.getEntities().getAttacklock());
            locked.addAll(entry.getEntities().getSpawnlockIds());
        }
        return id -> id != null && locked.contains(id);
    }

    /** Dimension-id predicate: direct lookups only. */
    public static Predicate<String> forDimensions(StageEntry entry) {
        Set<String> locked = new HashSet<>(entry.getDimensions());
        return id -> id != null && locked.contains(id);
    }

    /** Structure-id predicate: direct lookups; tags (id starts with "#") are matched too. */
    public static Predicate<String> forStructures(StageEntry entry) {
        Set<String> locked = new HashSet<>(entry.getStructures());
        return id -> id != null && locked.contains(id);
    }
}
