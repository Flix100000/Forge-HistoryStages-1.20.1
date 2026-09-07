package net.bananemdnsa.historystages.client;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.data.lock.TradeLockKind;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The notice says "this merchant is hiding trades from you", and saying it about the wrong
 * merchant is worse than not saying it at all — the player goes looking for a stage that has
 * nothing to do with the villager in front of them.
 *
 * <p>Only a window the server emptied ever gets a notice. A window it did not touch is never
 * mentioned, which means nothing arrives to overwrite the last one; so the rule that keeps them
 * apart is the container id, and that rule is what these check.
 */
class TradeLockNoticeTest {

    @BeforeEach
    void reset() {
        TradeLockNotice.clear();
    }

    @Test
    void theNoticeBelongsToTheWindowItWasSentFor() {
        TradeLockNotice.set(7, List.of("Bronze Age"), TradeLockKind.GLOBAL);

        assertTrue(TradeLockNotice.appliesTo(7));
        assertEquals(List.of("Bronze Age"), TradeLockNotice.stageNamesFor(7));
    }

    @Test
    void theNextMerchantIsNotAccusedOfHidingAnything() {
        TradeLockNotice.set(7, List.of("Bronze Age"), TradeLockKind.GLOBAL);

        assertFalse(TradeLockNotice.appliesTo(8),
                "opening a merchant whose offers are all visible sends nothing, so a notice that"
                        + " matched any window would still be sitting here and would be drawn"
                        + " over the next villager's perfectly good trades");
        assertEquals(List.of(), TradeLockNotice.stageNamesFor(8));
    }

    @Test
    void anEmptyStageListIsStillANotice() {
        TradeLockNotice.set(3, List.of(), TradeLockKind.GLOBAL);

        assertTrue(TradeLockNotice.appliesTo(3),
                "the window is empty on our account either way; whether the stages are named"
                        + " alongside is the player's config and not a reason to stay silent");
        assertEquals(List.of(), TradeLockNotice.stageNamesFor(3));
    }

    @Test
    void nothingIsClaimedBeforeAnyNoticeArrives() {
        assertFalse(TradeLockNotice.appliesTo(0),
                "container id 0 is the player's own inventory and a perfectly ordinary value —"
                        + " a fresh notice must not match it by accident");
        assertFalse(TradeLockNotice.appliesTo(-1));
        assertEquals(List.of(), TradeLockNotice.stageNamesFor(0));
    }

    @Test
    void leavingAServerForgetsIt() {
        TradeLockNotice.set(4, List.of("Bronze Age"), TradeLockKind.GLOBAL);
        TradeLockNotice.clear();

        assertFalse(TradeLockNotice.appliesTo(4),
                "container ids start over on the next server, so a kept notice would land on"
                        + " whichever window happens to reuse the number");
    }

    @Test
    void theStoredNamesCannotBeChangedAfterwards() {
        List<String> names = new ArrayList<>(List.of("Bronze Age"));
        TradeLockNotice.set(5, names, TradeLockKind.GLOBAL);
        names.add("Iron Age");

        assertEquals(List.of("Bronze Age"), TradeLockNotice.stageNamesFor(5));
    }

    @Test
    void theLockKindBelongsToItsWindowJustAsTheNamesDo() {
        TradeLockNotice.set(7, List.of("Bronze Age"), TradeLockKind.INDIVIDUAL);

        assertEquals(TradeLockKind.INDIVIDUAL, TradeLockNotice.kindFor(7));
        assertEquals(TradeLockKind.GLOBAL, TradeLockNotice.kindFor(8),
                "a window this notice is not about must not be told an individual stage emptied"
                        + " it — that is the same leak the container id exists to stop");
    }

    @Test
    void leavingAServerForgetsTheLockKindToo() {
        TradeLockNotice.set(4, List.of("Bronze Age"), TradeLockKind.DUAL);
        TradeLockNotice.clear();

        assertEquals(TradeLockKind.GLOBAL, TradeLockNotice.kindFor(4));
    }
}
