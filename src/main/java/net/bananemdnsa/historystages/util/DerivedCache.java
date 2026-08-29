package net.bananemdnsa.historystages.util;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Remembers a value derived from a key, for as long as the key itself is alive and only while the
 * settings it was derived under are unchanged.
 *
 * <p>Two properties do the invalidation, so there is no hook anywhere to forget:
 *
 * <ul>
 *   <li><b>Weak keys.</b> The entry lives exactly as long as something else still holds the key.
 *       For a key that belongs to a chunk, unloading the chunk drops the entry on its own, and a
 *       reloaded chunk brings a new key object and therefore a fresh derivation.</li>
 *   <li><b>A settings stamp.</b> Callers pass the settings the value was derived under, packed
 *       into one long. A stamp that does not match the stored one is a miss, so changing a config
 *       value re-derives rather than serving something computed under the old one.</li>
 * </ul>
 *
 * <p><strong>Only for values that are pure functions of key and settings</strong>, and only for
 * immutable ones: entries are handed out by reference and may be held by several callers at once.
 *
 * <p>Identity or equality is the key type's business. Given a key that does not override
 * {@code equals}, this behaves as an identity map, which is what a key like a
 * {@code StructureStart} wants.
 */
public final class DerivedCache<K, V> {

    private final Map<K, Entry<V>> entries = Collections.synchronizedMap(new WeakHashMap<>());

    private long hits;
    private long derivations;

    private record Entry<V>(long settings, V value) {}

    /**
     * The remembered value for {@code key} under {@code settings}, deriving it first if there is
     * none or if the stored one was derived under different settings.
     */
    public V get(K key, long settings, Supplier<V> derive) {
        Entry<V> stored = entries.get(key);
        if (stored != null && stored.settings() == settings) {
            hits++;
            return stored.value();
        }

        V value = derive.get();
        derivations++;
        entries.put(key, new Entry<>(settings, value));
        return value;
    }

    /** How often a stored value was reused. Counted so a test can prove the cache actually holds. */
    public long hits() {
        return hits;
    }

    /** How often {@code derive} had to run. A second call with the same key must not raise this. */
    public long derivations() {
        return derivations;
    }

    /** Live entries, ignoring any whose key has been collected but not yet expunged. */
    public int size() {
        return entries.size();
    }

    /**
     * Packs two int settings into the stamp {@link #get} compares. Two different pairs must not
     * collide, which is why this shifts rather than mixes.
     */
    public static long stamp(int first, int second) {
        return ((long) first << 32) | (second & 0xFFFFFFFFL);
    }
}
