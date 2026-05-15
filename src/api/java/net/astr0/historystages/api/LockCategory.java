package net.astr0.historystages.api;

import java.util.Map;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class LockCategory<T> {
    private final String id;
    protected final Map<T, BitSet> map;
    protected IStageManager manager;

    // Prevent unnecessary allocations of empty list objects. Just return the same one every time
    public static final List<StageDefinition> EMPTY_LIST = List.of();



    /**
     * @param id A unique identifier (e.g., "item", "dimension")
     * @param map The FastUtil map implementation tailored for this type.
     */
    public LockCategory(String id, Map<T, BitSet> map) {
        this.id = id;
        this.map = map;
    }

    public String getId() { return id; }

    public BitSet getLock(T key) {
        return map.get(key); // Returns null if not present, which is highly efficient
    }

    public void isLocked(T object, Player player) {
        BitSet lock = getLock(object);
        return manager.hasMissingStages(lock, player);
    }

        /**
     *
     * @param category Instance of {@link LockCategory} to check
     * @param lockedObject The object which you want to check the lock for.
     * @return
     * @param <T>
     */
    public <T> List<StageDefinition> getStagesFor(T lockedObject) {
        BitSet lock = getLock(lockedObject);
        if (lock == null) return EMPTY_LIST;

        return manager.getStageDefinitionsFromLock(lockedObject);
    }


    //TODO: Clean this up. Also check if we can do it in a more performance friendly way. For now, this will do
    public <T> List<StageDefinition> getMissingStageFor(T lockedObject, Player player) {
        return getStagesFor(category, lockedObject)
                .stream()
                .filter(
                        stage -> !manager.isStageUnlockedForPlayer(player, stage.getName())
                ).toList();
    }

    public void applyLock(T key, int bitIndex) {
        map.computeIfAbsent(key, k -> new BitSet()).set(bitIndex);
    }

    public void register(IStageManager manager) {
        this.manager = manager;
    }

    public void clear() {
        map.clear();
    }
}