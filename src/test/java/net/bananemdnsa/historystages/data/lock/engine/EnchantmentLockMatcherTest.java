package net.bananemdnsa.historystages.data.lock.engine;

import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.StageEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnchantmentLockMatcherTest {

    /** A stage that gates one enchanted book. level: null = no lvl key, Integer = exact, String = range. */
    private static StageEntry bookStage(String enchantmentId, Object level) {
        JsonObject ench = new JsonObject();
        ench.addProperty("id", enchantmentId);
        if (level instanceof Integer i) ench.addProperty("lvl", i);
        if (level instanceof String s) ench.addProperty("lvl", s);

        JsonArray stored = new JsonArray();
        stored.add(ench);

        JsonObject nbt = new JsonObject();
        nbt.add("StoredEnchantments", stored);

        StageEntry stage = new StageEntry();
        stage.setItemEntries(List.of(new ItemEntry("minecraft:enchanted_book", nbt)));
        return stage;
    }

    @Test
    void missingLevelLocksEveryLevel() {
        StageEntry stage = bookStage("minecraft:sharpness", null);
        assertTrue(EnchantmentLockMatcher.locksEnchantment(stage, "minecraft:sharpness", 1));
        assertTrue(EnchantmentLockMatcher.locksEnchantment(stage, "minecraft:sharpness", 5));
    }

    @Test
    void exactLevelLocksOnlyThatLevel() {
        StageEntry stage = bookStage("minecraft:sharpness", 3);
        assertTrue(EnchantmentLockMatcher.locksEnchantment(stage, "minecraft:sharpness", 3));
        assertFalse(EnchantmentLockMatcher.locksEnchantment(stage, "minecraft:sharpness", 2));
    }

    @Test
    void rangeLevelLocksInclusively() {
        StageEntry stage = bookStage("minecraft:sharpness", "2-4");
        assertFalse(EnchantmentLockMatcher.locksEnchantment(stage, "minecraft:sharpness", 1));
        assertTrue(EnchantmentLockMatcher.locksEnchantment(stage, "minecraft:sharpness", 2));
        assertTrue(EnchantmentLockMatcher.locksEnchantment(stage, "minecraft:sharpness", 4));
        assertFalse(EnchantmentLockMatcher.locksEnchantment(stage, "minecraft:sharpness", 5));
    }

    @Test
    void otherEnchantmentIdDoesNotMatch() {
        StageEntry stage = bookStage("minecraft:sharpness", 3);
        assertFalse(EnchantmentLockMatcher.locksEnchantment(stage, "minecraft:smite", 3));
    }

    @Test
    void nonBookItemEntriesAreIgnored() {
        StageEntry stage = new StageEntry();
        stage.setItemEntries(List.of(new ItemEntry("minecraft:iron_sword")));
        assertFalse(EnchantmentLockMatcher.locksEnchantment(stage, "minecraft:sharpness", 1));
    }

    @Test
    void emptyStageLocksNothing() {
        assertFalse(EnchantmentLockMatcher.locksEnchantment(new StageEntry(), "minecraft:sharpness", 1));
    }
}
