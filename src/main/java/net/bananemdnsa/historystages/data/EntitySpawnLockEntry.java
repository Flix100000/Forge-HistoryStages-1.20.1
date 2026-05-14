package net.bananemdnsa.historystages.data;

import java.util.ArrayList;
import java.util.List;

/**
 * A spawn-locked entity entry with an optional list of locked spawn sources.
 * When lockSources is null, all sources are locked (default behaviour).
 *
 * In JSON the field is written as {@code unlock_sources} (the complement — sources that are
 * NOT locked). {@code null} / no field means all sources are locked.
 */
public class EntitySpawnLockEntry {

    /** Canonical ordered list of all recognised spawn sources (medium granularity). */
    public static final List<String> ALL_SOURCES = List.of(
            "natural", "spawner", "structure", "breeding", "summon", "spawn_egg"
    );

    private final String id;
    private final List<String> lockSources; // null = all sources locked, empty = treated as all locked

    public EntitySpawnLockEntry(String id) {
        this.id = id;
        this.lockSources = null;
    }

    public EntitySpawnLockEntry(String id, List<String> lockSources) {
        this.id = id;
        this.lockSources = (lockSources != null && !lockSources.isEmpty()) ? new ArrayList<>(lockSources) : null;
    }

    public String getId() { return id; }

    /** Returns null if all sources are locked, otherwise the explicit list of locked sources. */
    public List<String> getLockSources() { return lockSources; }

    public boolean hasLockSources() { return lockSources != null && !lockSources.isEmpty(); }

    /** True if the given source is blocked by this entry. */
    public boolean blocksSource(String source) {
        return lockSources == null || lockSources.contains(source);
    }

    public EntitySpawnLockEntry copy() {
        return new EntitySpawnLockEntry(id, lockSources != null ? new ArrayList<>(lockSources) : null);
    }
}
