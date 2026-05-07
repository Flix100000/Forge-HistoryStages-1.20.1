package net.astr0.historystages.api;

import java.util.Map;
import java.util.BitSet;

public class LockCategory<T> {
    private final String id;
    protected final Map<T, BitSet> map;

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

    public void applyLock(T key, int bitIndex) {
        map.computeIfAbsent(key, k -> new BitSet()).set(bitIndex);
    }

    public void clear() {
        map.clear();
    }
}