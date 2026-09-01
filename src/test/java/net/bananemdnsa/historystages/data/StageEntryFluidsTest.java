package net.bananemdnsa.historystages.data;

import java.util.List;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageEntryFluidsTest {

    @Test
    void aFreshStageHasNoFluids() {
        assertTrue(new StageEntry().getFluidEntries().isEmpty());
    }

    @Test
    void fluidsRoundTripThroughTheSetter() {
        StageEntry stage = new StageEntry();
        stage.setFluidEntries(List.of(
                new FluidEntry("minecraft:lava"),
                new FluidEntry("create:molten_iron", List.of("use"), null, null)));

        assertEquals(2, stage.getFluidEntries().size());
        assertEquals("minecraft:lava", stage.getFluidEntries().get(0).getId());
        assertEquals(List.of("use"), stage.getFluidEntries().get(1).getLockActions());
    }

    @Test
    void getAllFluidIdsNamesEveryEntryOnce() {
        StageEntry stage = new StageEntry();
        stage.setFluidEntries(List.of(
                new FluidEntry("minecraft:lava"),
                new FluidEntry("minecraft:water")));

        assertEquals(List.of("minecraft:lava", "minecraft:water"), stage.getAllFluidIds());
    }

    /**
     * The copy has to be deep. A shallow one would let the editor's working copy write through
     * to the loaded stage, which is the shape of bug {@code copy()} exists to prevent.
     */
    @Test
    void copyDeepCopiesTheFluidEntries() {
        StageEntry stage = new StageEntry();
        stage.setFluidEntries(List.of(new FluidEntry("minecraft:lava")));

        StageEntry copy = stage.copy();

        assertEquals(1, copy.getFluidEntries().size());
        assertEquals("minecraft:lava", copy.getFluidEntries().get(0).getId());
        assertNotSame(stage.getFluidEntries().get(0), copy.getFluidEntries().get(0));
    }

    @Test
    void fluidsSurviveGsonInBothDirections() {
        StageEntry stage = new StageEntry();
        stage.setFluidEntries(List.of(
                new FluidEntry("minecraft:lava", List.of("use", "place"), null, null)));

        Gson gson = new Gson();
        StageEntry restored = gson.fromJson(gson.toJson(stage), StageEntry.class);

        assertEquals(1, restored.getFluidEntries().size());
        assertEquals("minecraft:lava", restored.getFluidEntries().get(0).getId());
        assertEquals(List.of("use", "place"), restored.getFluidEntries().get(0).getLockActions());
    }

    /**
     * The new list is written exactly like the ones beside it. Pinned against {@code items}
     * rather than against a literal, so this stays true whichever way the save path decides to
     * treat empty collections.
     */
    @Test
    void anEmptyFluidListIsWrittenTheSameWayAnEmptyItemListIs() {
        String json = new Gson().toJson(new StageEntry());
        assertEquals(json.contains("\"items\""), json.contains("\"fluids\""));
    }
}
