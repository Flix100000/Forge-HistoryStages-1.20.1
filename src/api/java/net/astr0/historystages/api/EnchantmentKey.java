package net.astr0.historystages.api;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;

/**
 * A cached, immutable representation of an enchantment and its level.
 * Used as a key in HistoryAPI.ENCHANTMENTS.
 */
public record EnchantmentKey(String id, int level) {

    // Primary cache: Namespace/Path -> (Level -> Instance)
    private static final Map<String, Int2ObjectMap<EnchantmentKey>> CACHE = new HashMap<>();

    /**
     * Static factory method to retrieve a cached instance.
     * Prevents redundant object instantiation for identical ID/Level pairs.
     */
    public static EnchantmentKey of(String id, int level) {
        // Compute the inner map for the specific enchantment ID if it doesn't exist
        Int2ObjectMap<EnchantmentKey> levelMap = CACHE.computeIfAbsent(id, k -> new Int2ObjectOpenHashMap<>());

        // Return the cached instance for this level, or create and cache it if missing
        return levelMap.computeIfAbsent(level, l -> new EnchantmentKey(id, l));
    }

    /**
     * Clears the cache.
     * Useful during the 'bake' phase if you want to ensure no orphaned instances
     * persist across configuration reloads.
     */
    public static void clearCache() {
        CACHE.clear();
    }
}
