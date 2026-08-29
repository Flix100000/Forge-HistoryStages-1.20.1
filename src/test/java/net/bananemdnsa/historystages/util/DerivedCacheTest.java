package net.bananemdnsa.historystages.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What {@link DerivedCache} keeps and what it refuses to keep.
 *
 * <p>A cache that never holds is only slow, which is exactly the failure nothing notices — so the
 * derivation counter is asserted directly rather than inferred from the value coming back.
 */
class DerivedCacheTest {

    /** A key with no {@code equals} of its own, like the {@code StructureStart} this cache serves. */
    private static final class Key {}

    private static final long SETTINGS = DerivedCache.stamp(4, 6);

    @Test
    void aSecondAskForTheSameKeyDoesNotDeriveAgain() {
        DerivedCache<Key, String> cache = new DerivedCache<>();
        AtomicInteger derived = new AtomicInteger();
        Key key = new Key();

        String first = cache.get(key, SETTINGS, () -> "zone-" + derived.incrementAndGet());
        String second = cache.get(key, SETTINGS, () -> "zone-" + derived.incrementAndGet());

        assertEquals(1, derived.get(), "the supplier ran twice, so nothing was remembered");
        assertSame(first, second, "the second call built a new value instead of reusing the stored one");
        assertEquals(1, cache.derivations());
        assertEquals(1, cache.hits());
    }

    @Test
    void aDifferentKeyDerivesItsOwnValue() {
        DerivedCache<Key, String> cache = new DerivedCache<>();
        AtomicInteger derived = new AtomicInteger();

        cache.get(new Key(), SETTINGS, () -> "zone-" + derived.incrementAndGet());
        cache.get(new Key(), SETTINGS, () -> "zone-" + derived.incrementAndGet());

        // Two keys that are equal only to themselves must not share an entry. Were the key type
        // ever given a value-based equals, this is what would catch it.
        assertEquals(2, derived.get(), "two distinct keys shared one entry");
    }

    @Test
    void changedSettingsDeriveAgain() {
        DerivedCache<Key, String> cache = new DerivedCache<>();
        AtomicInteger derived = new AtomicInteger();
        Key key = new Key();

        cache.get(key, DerivedCache.stamp(4, 6), () -> "zone-" + derived.incrementAndGet());
        cache.get(key, DerivedCache.stamp(9, 6), () -> "zone-" + derived.incrementAndGet());

        assertEquals(2, derived.get(),
                "a value derived under the old settings was served after they changed");
    }

    @Test
    void settingsGoBackToBeingAHitWhenTheyReturn() {
        DerivedCache<Key, String> cache = new DerivedCache<>();
        AtomicInteger derived = new AtomicInteger();
        Key key = new Key();

        cache.get(key, DerivedCache.stamp(4, 6), () -> "zone-" + derived.incrementAndGet());
        cache.get(key, DerivedCache.stamp(9, 6), () -> "zone-" + derived.incrementAndGet());
        cache.get(key, DerivedCache.stamp(9, 6), () -> "zone-" + derived.incrementAndGet());

        assertEquals(2, derived.get(), "the entry was not replaced by the new settings' value");
    }

    @Test
    void distinctSettingPairsGetDistinctStamps() {
        // The pair is packed, not mixed, precisely so this holds. A stamp built as
        // first * 31 + second collides here, and a collision serves a zone built under the
        // wrong padding - which looks like a lock zone of the wrong size, not like a cache bug.
        assertNotEquals(DerivedCache.stamp(1, 0), DerivedCache.stamp(0, 1));
        assertNotEquals(DerivedCache.stamp(2, 0), DerivedCache.stamp(0, 62));
        assertNotEquals(DerivedCache.stamp(0, -1), DerivedCache.stamp(-1, 0));
        assertEquals(DerivedCache.stamp(7, 3), DerivedCache.stamp(7, 3));
    }
}
