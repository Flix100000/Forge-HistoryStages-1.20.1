package net.bananemdnsa.historystages.client.editor.nbt;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtCriteriaValidatorTest {

    private static final Map<String, Integer> LIMITS = Map.of("minecraft:sharpness", 5);

    private static NbtCriteriaValidator validator() {
        return new NbtCriteriaValidator(LIMITS::get, id -> id.equals("minecraft:strength"));
    }

    private static EnchantmentListCriterion ench(String id, String level) {
        EnchantmentListCriterion criterion = new EnchantmentListCriterion("Enchantments");
        criterion.lines.add(new EnchantmentListCriterion.Line(id, level));
        return criterion;
    }

    @Test
    void aValidEnchantmentRaisesNothing() {
        assertTrue(validator().validate(List.of(ench("minecraft:sharpness", "5"))).isEmpty());
    }

    @Test
    void anUnknownEnchantmentIsReported() {
        List<NbtCriteriaValidator.Warning> warnings =
                validator().validate(List.of(ench("minecraft:nonsense", "1")));

        assertEquals(1, warnings.size());
        assertEquals(NbtCriteriaValidator.Kind.UNKNOWN_ENCHANTMENT, warnings.get(0).kind());
        assertEquals(0, warnings.get(0).lineIndex());
    }

    @Test
    void aLevelAboveTheMaximumIsReported() {
        List<NbtCriteriaValidator.Warning> warnings =
                validator().validate(List.of(ench("minecraft:sharpness", "7")));

        assertEquals(1, warnings.size());
        assertEquals(NbtCriteriaValidator.Kind.LEVEL_TOO_HIGH, warnings.get(0).kind());
        assertEquals(5, warnings.get(0).limit());
        assertEquals(7, warnings.get(0).actual(), "the warning has to name the level that was typed");
    }

    @Test
    void onlyTheUpperEndOfARangeIsChecked() {
        assertTrue(validator().validate(List.of(ench("minecraft:sharpness", "1-5"))).isEmpty());
        assertEquals(NbtCriteriaValidator.Kind.LEVEL_TOO_HIGH,
                validator().validate(List.of(ench("minecraft:sharpness", "1-9"))).get(0).kind());
    }

    @Test
    void anUnknownPotionIsReported() {
        List<NbtCriteriaValidator.Warning> warnings = validator().validate(
                List.of(new CustomDataCriterion("Potion", "minecraft:nonsense", true)));

        assertEquals(1, warnings.size());
        assertEquals(NbtCriteriaValidator.Kind.UNKNOWN_POTION, warnings.get(0).kind());
    }

    @Test
    void anEmptyEnchantmentLineIsIgnored() {
        assertTrue(validator().validate(List.of(ench("", "3"))).isEmpty());
    }
}
