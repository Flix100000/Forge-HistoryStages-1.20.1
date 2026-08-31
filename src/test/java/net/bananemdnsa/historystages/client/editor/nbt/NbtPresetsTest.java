package net.bananemdnsa.historystages.client.editor.nbt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtPresetsTest {

    @Test
    void everyPresetPointsAtAComponent() {
        assertFalse(NbtPresets.common().isEmpty(), "the 'common' tab would be empty");
        for (NbtPresets.Preset preset : NbtPresets.common()) {
            assertTrue(preset.componentId().contains(":"),
                    preset.nameKey() + " has no namespaced component id: " + preset.componentId());
            assertNotNull(preset.nameKey());
            assertNotNull(preset.descriptionKey());
        }
    }

    @Test
    void legacyKeysAreRecognised() {
        assertTrue(NbtPresets.isLegacyKey("Unbreakable"));
        assertTrue(NbtPresets.isLegacyKey("CustomModelData"));
        assertTrue(NbtPresets.isLegacyKey("RepairCost"));
        assertTrue(NbtPresets.isLegacyKey("Potion"));
        assertTrue(NbtPresets.isLegacyKey("display"));
    }

    @Test
    void anOrdinaryKeyIsNotFlagged() {
        assertFalse(NbtPresets.isLegacyKey("quest"));
        assertFalse(NbtPresets.isLegacyKey("Enchantments"));
    }

    /**
     * The two enchantment lists mean different things — one is what an item is enchanted with, the
     * other what a book is carrying. They shared a description once and were indistinguishable in
     * the editor because of it.
     */
    @Test
    void theTwoEnchantmentListsNeverShareWording() {
        assertNotEquals(NbtPresets.enchantmentNameKey(NbtPresets.ENCHANTMENTS),
                NbtPresets.enchantmentNameKey(NbtPresets.STORED_ENCHANTMENTS));
        assertNotEquals(NbtPresets.enchantmentDescriptionKey(NbtPresets.ENCHANTMENTS),
                NbtPresets.enchantmentDescriptionKey(NbtPresets.STORED_ENCHANTMENTS));
    }

    @Test
    void aLegacyKeyResolvesToItsComponent() {
        assertEquals("minecraft:unbreakable", NbtPresets.componentForLegacyKey("Unbreakable"));
        assertEquals("minecraft:custom_model_data", NbtPresets.componentForLegacyKey("CustomModelData"));
        assertNull(NbtPresets.componentForLegacyKey("quest"));
    }
}
