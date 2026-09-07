package net.bananemdnsa.historystages.data;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageEntryTradesTest {

    @Test
    void aFreshStageHasThreeEmptyTradeLists() {
        StageEntry stage = new StageEntry();
        assertTrue(stage.getTradeOffers().isEmpty());
        assertTrue(stage.getTradeProfessions().isEmpty());
        assertTrue(stage.getTradeLevels().isEmpty());
    }

    @Test
    void eachListIsSetAndReadBackOnItsOwn() {
        StageEntry stage = new StageEntry();
        stage.setTradeOffers(List.of(new TradeOfferEntry("minecraft:librarian", 1,
                "minecraft:diamond", "minecraft:emerald", null)));
        stage.setTradeProfessions(List.of("minecraft:librarian"));
        stage.setTradeLevels(List.of("4", "5"));

        assertEquals(List.of("minecraft:diamond"), stage.getAllTradeItemIds());
        assertEquals(List.of("minecraft:librarian"), stage.getTradeProfessions());
        assertEquals(List.of("4", "5"), stage.getTradeLevels());
    }

    @Test
    void theCallersListDoesNotStayConnected() {
        List<String> professions = new ArrayList<>(List.of("minecraft:librarian"));
        StageEntry stage = new StageEntry();
        stage.setTradeProfessions(professions);
        professions.add("minecraft:cleric");

        assertEquals(List.of("minecraft:librarian"), stage.getTradeProfessions());
    }

    /**
     * {@code copy()} is where a forgotten field disappears without a sound: the editor loads a
     * stage, saves it back through a copy, and the entries are simply gone.
     */
    @Test
    void allThreeListsSurviveACopyAndAreIndependent() {
        StageEntry stage = new StageEntry();
        stage.setTradeOffers(List.of(new TradeOfferEntry("minecraft:librarian", 2,
                "minecraft:emerald", "minecraft:paper", null)));
        stage.setTradeProfessions(List.of("minecraft:librarian"));
        stage.setTradeLevels(List.of("4"));

        StageEntry copy = stage.copy();

        assertEquals(List.of("minecraft:emerald"), copy.getAllTradeItemIds());
        assertEquals(2, copy.getTradeOffers().get(0).level());
        assertEquals("minecraft:paper", copy.getTradeOffers().get(0).takesAId());
        assertEquals(List.of("minecraft:librarian"), copy.getTradeProfessions());
        assertEquals(List.of("4"), copy.getTradeLevels());

        assertNotSame(stage.getTradeOffers(), copy.getTradeOffers(),
                "a shared list lets an edit in the editor reach the loaded stage");
    }
}
