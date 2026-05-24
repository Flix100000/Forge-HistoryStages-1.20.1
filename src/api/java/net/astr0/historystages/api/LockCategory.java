package net.astr0.historystages.api;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
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

    public boolean isLocked(T object, Player player) {
        BitSet lock = getLock(object);
        return manager.hasMissingStages(lock, player);
    }

    public boolean hasFlag(T object, int lockFlag) {
        return getLock(object).get(lockFlag);
    }

        /**
     *
     * @param category Instance of {@link LockCategory} to check
     * @param lockedObject The object which you want to check the lock for.
     * @return
     * @param
     */
    public List<StageDefinition> getStagesFor(T lockedObject, StageScope scope) {
        BitSet lock = getLock(lockedObject);
        if (lock == null) return EMPTY_LIST;

        return manager.getStageDefinitionsFromLock(lock, scope);
    }


    //TODO: Clean this up. Also check if we can do it in a more performance friendly way. For now, this will do
    public List<StageDefinition> getMissingStagesFor(T lockedObject, Player player, StageScope scope) {
        return getStagesFor(lockedObject, scope)
                .stream()
                .filter(
                        stage -> !manager.isStageUnlockedForPlayer(player, stage.getName())
                        //&& (scope == StageScope.ALL || stage.getScope() == scope)
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