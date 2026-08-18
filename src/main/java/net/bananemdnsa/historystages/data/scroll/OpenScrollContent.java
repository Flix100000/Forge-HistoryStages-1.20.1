package net.bananemdnsa.historystages.data.scroll;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.EntityLocks;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link StageEntry} into the {@link OpenScrollDocument} the open scroll screen draws.
 *
 * <p>The only thing this needs from Minecraft is "which items are in this tag", and that arrives
 * through {@link TagResolver}. Everything else — grouping, deduplicating, counting — is plain data
 * work and is unit-tested without a running game.
 */
public final class OpenScrollContent {

    private OpenScrollContent() {}

    public static final String DIMENSIONS_KEY = "gui.historystages.open_scroll.world.dimensions";
    public static final String STRUCTURES_KEY = "gui.historystages.open_scroll.world.structures";
    public static final String BIOMES_KEY = "gui.historystages.open_scroll.world.biomes";

    /** The item ids behind an item tag. The client implementation asks the item registry. */
    public interface TagResolver {
        List<String> itemsInTag(String tagId);
    }

    public static OpenScrollDocument build(String stageId, boolean individual, StageEntry entry,
                                           TagResolver tags) {
        return build(stageId, individual, entry, tags, "");
    }

    public static OpenScrollDocument build(String stageId, boolean individual, StageEntry entry,
                                           TagResolver tags, String description) {
        return new OpenScrollDocument(stageId, individual,
                entry.getIcon(),
                entry.getDisplayName() == null ? "" : entry.getDisplayName(),
                description == null ? "" : description,
                items(entry, tags), creatures(entry.getEntities()), world(entry));
    }

    /** A document for a scroll whose stage no longer exists, so the screen can say so. */
    public static OpenScrollDocument unknown(String stageId) {
        return new OpenScrollDocument(stageId, false, "", "", "", List.of(), List.of(), List.of());
    }

    /** Direct items first, then the ones behind the tags, each in config order, no repeats. */
    private static List<String> items(StageEntry entry, TagResolver tags) {
        LinkedHashSet<String> out = new LinkedHashSet<>(entry.getAllItemIds());
        for (String tag : entry.getNbtFreeTags()) {
            out.addAll(tags.itemsInTag(tag));
        }
        return new ArrayList<>(out);
    }

    /** All three lock kinds in one list; an entity in several of them keeps every marker. */
    private static List<OpenScrollEntry> creatures(EntityLocks locks) {
        Map<String, EnumSet<OpenScrollMarker>> byId = new LinkedHashMap<>();
        for (String id : locks.getSpawnlockIds()) {
            byId.computeIfAbsent(id, k -> EnumSet.noneOf(OpenScrollMarker.class)).add(OpenScrollMarker.SPAWN);
        }
        for (String id : locks.getAttacklock()) {
            byId.computeIfAbsent(id, k -> EnumSet.noneOf(OpenScrollMarker.class)).add(OpenScrollMarker.ATTACK);
        }
        for (String id : locks.getInteractionlockIds()) {
            byId.computeIfAbsent(id, k -> EnumSet.noneOf(OpenScrollMarker.class)).add(OpenScrollMarker.INTERACTION);
        }
        List<OpenScrollEntry> out = new ArrayList<>(byId.size());
        byId.forEach((id, markers) -> out.add(new OpenScrollEntry(id, markers)));
        return out;
    }

    /** Dimensions, structures, biomes — a group with nothing in it gets no heading. */
    private static List<OpenScrollWorldGroup> world(StageEntry entry) {
        List<OpenScrollWorldGroup> out = new ArrayList<>(3);
        addGroup(out, DIMENSIONS_KEY, entry.getDimensions());
        addGroup(out, STRUCTURES_KEY, entry.getStructures());
        addGroup(out, BIOMES_KEY, entry.getBiomes());
        return out;
    }

    private static void addGroup(List<OpenScrollWorldGroup> out, String key, List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        out.add(new OpenScrollWorldGroup(key, ids));
    }
}
