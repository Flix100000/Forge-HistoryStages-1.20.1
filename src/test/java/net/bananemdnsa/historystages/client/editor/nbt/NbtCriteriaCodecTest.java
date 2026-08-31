package net.bananemdnsa.historystages.client.editor.nbt;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtCriteriaCodecTest {

    private static final Gson GSON = new Gson();

    private static JsonObject json(String raw) {
        return GSON.fromJson(raw, JsonObject.class);
    }

    @Test
    void nothingLoadsToNoCriteria() {
        assertTrue(NbtCriteriaCodec.load(null).isEmpty());
        assertTrue(NbtCriteriaCodec.load(new JsonObject()).isEmpty());
    }

    @Test
    void enchantmentsLoadAsTheirOwnCriterion() {
        List<NbtCriterion> loaded = NbtCriteriaCodec.load(json("""
                {"Enchantments":[{"id":"minecraft:sharpness","lvl":5},
                                 {"id":"minecraft:looting","lvl":"1-3"}]}
                """));

        assertEquals(1, loaded.size());
        EnchantmentListCriterion ench = assertInstanceOf(EnchantmentListCriterion.class, loaded.get(0));
        assertEquals("Enchantments", ench.key());
        assertEquals(2, ench.lines.size());
        assertEquals("minecraft:sharpness", ench.lines.get(0).id);
        assertEquals("5", ench.lines.get(0).level);
        assertEquals("1-3", ench.lines.get(1).level);
    }

    @Test
    void aComponentLoadsAsAComponentCriterion() {
        List<NbtCriterion> loaded = NbtCriteriaCodec.load(json("""
                {"components":{"minecraft:custom_name":"{\\"text\\":\\"Excalibur\\"}"}}
                """));

        assertEquals(1, loaded.size());
        ComponentCriterion comp = assertInstanceOf(ComponentCriterion.class, loaded.get(0));
        assertEquals("minecraft:custom_name", comp.componentId());
        assertEquals("components.minecraft:custom_name", comp.identity());
    }

    @Test
    void loreLoadsAsATextList() {
        List<NbtCriterion> loaded = NbtCriteriaCodec.load(json("""
                {"components":{"minecraft:lore":["line one","line two"]}}
                """));

        TextListCriterion lore = assertInstanceOf(TextListCriterion.class, loaded.get(0));
        assertEquals(List.of("line one", "line two"), lore.lines);
    }

    @Test
    void anUnknownTopLevelKeyLoadsAsCustomData() {
        List<NbtCriterion> loaded = NbtCriteriaCodec.load(json("""
                {"quest":"main_01"}
                """));

        CustomDataCriterion custom = assertInstanceOf(CustomDataCriterion.class, loaded.get(0));
        assertEquals("quest", custom.key);
        assertEquals("main_01", custom.valueText);
        assertFalse(custom.legacySuspect);
    }

    @Test
    void aLegacyKeyLoadsAsCustomDataAndIsFlagged() {
        List<NbtCriterion> loaded = NbtCriteriaCodec.load(json("""
                {"Unbreakable":true}
                """));

        CustomDataCriterion custom = assertInstanceOf(CustomDataCriterion.class, loaded.get(0));
        assertEquals("Unbreakable", custom.key);
        assertEquals("true", custom.valueText);
        assertTrue(custom.legacySuspect, "a legacy key has to offer the conversion hint");
    }

    @Test
    void writingUndoesLoading() {
        String raw = """
                {"Enchantments":[{"id":"minecraft:sharpness","lvl":5}],
                 "components":{"minecraft:lore":["a line"],"minecraft:unbreakable":{}},
                 "quest":"main_01",
                 "tier":3}
                """;

        JsonObject written = NbtCriteriaCodec.write(NbtCriteriaCodec.load(json(raw)));

        assertEquals(json(raw), written);
    }

    @Test
    void aNumericCustomValueStaysNumeric() {
        JsonObject written = NbtCriteriaCodec.write(
                List.of(new CustomDataCriterion("level", "5", false)));

        assertTrue(written.get("level").getAsJsonPrimitive().isNumber(),
                "a number written back as a string can never match a NumericTag");
        assertEquals(5, written.get("level").getAsInt());
    }

    @Test
    void aRangeStaysAString() {
        JsonObject written = NbtCriteriaCodec.write(
                List.of(new CustomDataCriterion("level", "1-4", false)));

        assertTrue(written.get("level").getAsJsonPrimitive().isString());
        assertEquals("1-4", written.get("level").getAsString());
    }

    @Test
    void emptyCriteriaAreDropped() {
        JsonObject written = NbtCriteriaCodec.write(List.of(
                new ComponentCriterion("minecraft:custom_name", "  ", null),
                new CustomDataCriterion("", "x", false),
                new EnchantmentListCriterion("Enchantments")));

        assertEquals(0, written.size());
    }

    @Test
    void aTextComponentIsTypedWithoutItsQuotes() {
        ComponentCriterion name = new ComponentCriterion("minecraft:custom_name", "", null);
        name.setFromDisplay("Excalibur");

        assertEquals("\"Excalibur\"", name.valueJson, "the matcher compares against a JSON string");
        assertEquals("Excalibur", name.displayValue(), "the field must not show the quotes");
    }

    @Test
    void aNumberComponentStaysANumber() {
        ComponentCriterion cost = new ComponentCriterion("minecraft:repair_cost", "", null);
        cost.setFromDisplay("3");

        JsonObject written = NbtCriteriaCodec.write(List.of(cost));
        assertTrue(written.getAsJsonObject("components").get("minecraft:repair_cost")
                .getAsJsonPrimitive().isNumber());
    }

    @Test
    void aNumberComponentKeepsARangeAsAString() {
        ComponentCriterion cost = new ComponentCriterion("minecraft:repair_cost", "", null);
        cost.setFromDisplay("1-4");

        JsonObject written = NbtCriteriaCodec.write(List.of(cost));
        assertEquals("1-4", written.getAsJsonObject("components").get("minecraft:repair_cost").getAsString());
    }

    @Test
    void nonsenseInANumberFieldIsRejectedRatherThanStored() {
        ComponentCriterion cost = new ComponentCriterion("minecraft:repair_cost", "", null);
        cost.setFromDisplay("drei");

        assertTrue(cost.isEmpty(), "a value the matcher could never use must not reach the file");
    }

    @Test
    void aPresenceComponentLoadsWithoutAValueToEdit() {
        List<NbtCriterion> loaded = NbtCriteriaCodec.load(json("""
                {"components":{"minecraft:unbreakable":{}}}
                """));

        ComponentCriterion comp = assertInstanceOf(ComponentCriterion.class, loaded.get(0));
        assertEquals(ValueKind.PRESENCE, comp.valueKind);
        assertFalse(comp.isEmpty(), "presence is the whole criterion — it must survive the save");
    }

    @Test
    void anUnknownComponentStaysRawJson() {
        List<NbtCriterion> loaded = NbtCriteriaCodec.load(json("""
                {"components":{"somemod:spell_container":{"spells":[]}}}
                """));

        ComponentCriterion comp = assertInstanceOf(ComponentCriterion.class, loaded.get(0));
        assertEquals(ValueKind.JSON, comp.valueKind,
                "guessing a shape for a mod component is how criteria stop matching");
    }

    @Test
    void anUnparseableComponentValueIsDropped() {
        JsonObject written = NbtCriteriaCodec.write(
                List.of(new ComponentCriterion("minecraft:custom_name", "{not json", null)));

        assertEquals(0, written.size());
    }
}
