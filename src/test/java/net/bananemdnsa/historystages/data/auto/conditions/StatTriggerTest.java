package net.bananemdnsa.historystages.data.auto.conditions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatTriggerTest {

    @Test
    void everyCategoryParsesBackToItself() {
        for (StatCategory c : StatCategory.values()) {
            assertEquals(c, StatCategory.parse(c.serialize()));
        }
    }

    @Test
    void anUnknownCategoryIsNullRatherThanADefault() {
        assertNull(StatCategory.parse("mined_but_typo"));
        assertNull(StatCategory.parse(null));
    }

    @Test
    void theThresholdIsMet() {
        StatTrigger t = new StatTrigger("used", "minecraft:diamond_pickaxe", 50);

        assertFalse(t.matches(49));
        assertTrue(t.matches(50));
        assertTrue(t.matches(51));
    }

    /** A threshold of 0 would be met by every player before they did anything at all. */
    @Test
    void aZeroOrNegativeCountIsRaisedToOne() {
        assertEquals(1, new StatTrigger("custom", "minecraft:fish_caught", 0).requiredCount());
        assertEquals(1, new StatTrigger("custom", "minecraft:fish_caught", -5).requiredCount());
        assertFalse(new StatTrigger("custom", "minecraft:fish_caught", 0).matches(0));
        assertTrue(new StatTrigger("custom", "minecraft:fish_caught", 0).matches(1));
    }

    /**
     * The signature is the identity progress is stored against. It has to be built from the
     * normalised count, or the same trigger changes identity the first time it is re-saved.
     */
    @Test
    void theSignatureIsBuiltFromTheNormalisedCount() {
        assertEquals(new StatTrigger("custom", "minecraft:fish_caught", 1).signature(),
                new StatTrigger("custom", "minecraft:fish_caught", 0).signature());
    }

    @Test
    void theSignatureSeparatesCategoryIdAndCount() {
        StatTrigger base = new StatTrigger("used", "minecraft:stone", 5);

        assertNotEquals(base.signature(), new StatTrigger("mined", "minecraft:stone", 5).signature());
        assertNotEquals(base.signature(), new StatTrigger("used", "minecraft:dirt", 5).signature());
        assertNotEquals(base.signature(), new StatTrigger("used", "minecraft:stone", 6).signature());
    }

    @Test
    void theSameValuesGiveTheSameSignature() {
        assertEquals(new StatTrigger("used", "minecraft:stone", 5).signature(),
                new StatTrigger("used", "minecraft:stone", 5).signature());
    }

    @Test
    void theTypeDiscriminatorIsStat() {
        assertEquals("stat", new StatTrigger("used", "minecraft:stone", 5).type());
    }
}
