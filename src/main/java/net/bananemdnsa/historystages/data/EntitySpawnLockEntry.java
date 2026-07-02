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
    private final List<String> unlockDimensions; // null/empty = all dimensions locked; otherwise the dims that are NOT locked

    public EntitySpawnLockEntry(String id) {
        this(id, null, null);
    }

    public EntitySpawnLockEntry(String id, List<String> lockSources) {
        this(id, lockSources, null);
    }

    public EntitySpawnLockEntry(String id, List<String> lockSources, List<String> unlockDimensions) {
        this.id = id;
        this.lockSources = (lockSources != null && !lockSources.isEmpty()) ? new ArrayList<>(lockSources) : null;
        this.unlockDimensions = (unlockDimensions != null && !unlockDimensions.isEmpty()) ? new ArrayList<>(unlockDimensions) : null;
    }

    public String getId() { return id; }

    /** Returns null if all sources are locked, otherwise the explicit list of locked sources. */
    public List<String> getLockSources() { return lockSources; }

    public boolean hasLockSources() { return lockSources != null && !lockSources.isEmpty(); }

    /** True if the given source is blocked by this entry. */
    public boolean blocksSource(String source) {
        return lockSources == null || lockSources.contains(source);
    }

    /** Returns null if all dimensions are locked, otherwise the explicit list of unlocked (allowed) dimensions. */
    public List<String> getUnlockDimensions() { return unlockDimensions; }

    public boolean hasUnlockDimensions() { return unlockDimensions != null && !unlockDimensions.isEmpty(); }

    /** True if the given dimension is blocked by this entry. Anything not explicitly unlocked is blocked. */
    public boolean blocksDimension(String dimension) {
        return unlockDimensions == null || unlockDimensions.isEmpty() || !unlockDimensions.contains(dimension);
    }

    public EntitySpawnLockEntry copy() {
        return new EntitySpawnLockEntry(
                id,
                lockSources != null ? new ArrayList<>(lockSources) : null,
                unlockDimensions != null ? new ArrayList<>(unlockDimensions) : null);
    }
}
