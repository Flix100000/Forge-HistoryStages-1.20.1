package net.bananemdnsa.historystages.data.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which lock the trade window's notice shows.
 *
 * <p>The wire values are pinned here because they cross a network boundary: changing one silently
 * would show a mismatched client the wrong lock, and nothing else in the mod would notice.
 */
class TradeLockKindTest {

    @Test
    @DisplayName("every kind survives being written and read back")
    void roundTrip() {
        for (TradeLockKind kind : TradeLockKind.values()) {
            assertEquals(kind, TradeLockKind.fromCode(kind.code()));
        }
    }

    @Test
    @DisplayName("an unknown code reads as global rather than throwing")
    void unknownCodeFallsBack() {
        assertEquals(TradeLockKind.GLOBAL, TradeLockKind.fromCode(99));
        assertEquals(TradeLockKind.GLOBAL, TradeLockKind.fromCode(-1));
    }

    @Test
    @DisplayName("global and individual stages together are the dual lock")
    void bothIsDual() {
        assertEquals(TradeLockKind.DUAL, TradeLockKind.of(true, true));
        assertEquals(TradeLockKind.GLOBAL, TradeLockKind.of(true, false));
        assertEquals(TradeLockKind.INDIVIDUAL, TradeLockKind.of(false, true));
    }

    @Test
    @DisplayName("neither is global, because a notice is only sent when something did lock it")
    void neitherIsGlobal() {
        assertEquals(TradeLockKind.GLOBAL, TradeLockKind.of(false, false));
    }
}
