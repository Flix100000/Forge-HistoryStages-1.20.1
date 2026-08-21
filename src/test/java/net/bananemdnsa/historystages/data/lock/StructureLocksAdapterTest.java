package net.bananemdnsa.historystages.data.lock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StructureLocksAdapterTest {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(StructureLocks.class, new StructureLocksAdapter())
            .create();

    private static StructureLocks parse(String json) {
        return GSON.fromJson(json, StructureLocks.class);
    }

    @Test
    void legacyStringEntriesParseAsBlockRules() {
        StructureLocks locks = parse("""
                {"structures":["minecraft:village_plains"],
                 "block_generation":["minecraft:woodland_mansion"]}""");

        assertEquals(1, locks.getGenerationRules().size());
        assertEquals(StructureGenerationRule.blockEntirely("minecraft:woodland_mansion"),
                locks.getGenerationRules().get(0));
    }

    @Test
    void legacyEntriesAreWrittenBackAsBareStrings() {
        String out = GSON.toJson(parse("""
                {"structures":[],"block_generation":["minecraft:woodland_mansion"]}"""));

        assertEquals(
                JsonParser.parseString("""
                        {"structures":[],"block_generation":["minecraft:woodland_mansion"]}"""),
                JsonParser.parseString(out),
                "files that never touch limits must stay in the old shape");
    }

    @Test
    void objectEntriesRoundTrip() {
        String json = """
                {"structures":[],"block_generation":[
                  {"id":"#minecraft:village","phase":"while_locked","max":3},
                  {"id":"minecraft:ancient_city","phase":"after_unlock","max":1,"reset_on_relock":true}]}""";

        StructureLocks locks = parse(json);
        assertEquals(List.of(
                new StructureGenerationRule("#minecraft:village", GenerationPhase.WHILE_LOCKED, 3, false),
                new StructureGenerationRule("minecraft:ancient_city", GenerationPhase.AFTER_UNLOCK, 1, true)),
                locks.getGenerationRules());

        assertEquals(JsonParser.parseString(json), JsonParser.parseString(GSON.toJson(locks)));
    }

    @Test
    void resetFlagIsOmittedWhenFalse() {
        StructureLocks locks = new StructureLocks();
        locks.setGenerationRules(List.of(
                new StructureGenerationRule("a:b", GenerationPhase.WHILE_LOCKED, 2, false)));

        assertFalse(GSON.toJson(locks).contains("reset_on_relock"));
    }

    @Test
    void ruleWithoutIdIsDropped() {
        StructureLocks locks = parse("""
                {"structures":[],"block_generation":[{"phase":"after_unlock","max":2}]}""");
        assertTrue(locks.getGenerationRules().isEmpty());
    }

    @Test
    void ruleWithAnEmptyIdIsDropped() {
        StructureLocks locks = parse("""
                {"structures":[],"block_generation":[{"id":"","phase":"after_unlock","max":2}]}""");
        assertTrue(locks.getGenerationRules().isEmpty());
    }

    @Test
    void unknownKeysInsideARuleAreSkipped() {
        StructureLocks locks = parse("""
                {"structures":[],"block_generation":[
                  {"id":"a:b","phase":"after_unlock","max":2,"future_field":{"nested":[1,2]}}]}""");

        assertEquals(List.of(new StructureGenerationRule("a:b", GenerationPhase.AFTER_UNLOCK, 2, false)),
                locks.getGenerationRules());
    }

    @Test
    void legacyStringsAndRulesMixInOneArray() {
        String json = """
                {"structures":[],"block_generation":[
                  "minecraft:woodland_mansion",
                  {"id":"#minecraft:village","phase":"while_locked","max":3}]}""";

        StructureLocks locks = parse(json);
        assertEquals(List.of(
                StructureGenerationRule.blockEntirely("minecraft:woodland_mansion"),
                new StructureGenerationRule("#minecraft:village", GenerationPhase.WHILE_LOCKED, 3, false)),
                locks.getGenerationRules());

        assertEquals(JsonParser.parseString(json), JsonParser.parseString(GSON.toJson(locks)));
    }

    @Test
    void rulesWithoutAnIdAreDroppedOnTheWayIn() {
        // The writer emits a legacy rule as a bare string, so a null id would land in the file as
        // a JSON null that the reader cannot parse. It must never get that far.
        StructureLocks locks = new StructureLocks();
        locks.setGenerationRules(java.util.Arrays.asList(
                new StructureGenerationRule("a:b", GenerationPhase.WHILE_LOCKED, 0, false),
                new StructureGenerationRule(null, GenerationPhase.WHILE_LOCKED, 0, false),
                new StructureGenerationRule("", GenerationPhase.WHILE_LOCKED, 0, false)));

        assertEquals(List.of(StructureGenerationRule.blockEntirely("a:b")), locks.getGenerationRules());
    }

    @Test
    void emptyGenerationListIsOmittedEntirely() {
        assertFalse(GSON.toJson(new StructureLocks()).contains("block_generation"));
    }
}
