package net.bananemdnsa.historystages.compat.script;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ScriptStageListenersTest {

    @BeforeEach
    void reset() {
        ScriptStageListeners.clear();
    }

    @AfterEach
    void restoreSink() {
        ScriptStageListeners.resetErrorSink();
    }

    @Test
    void aRegisteredListenerIsCalled() {
        List<String> seen = new ArrayList<>();
        ScriptStageListeners.onUnlocked((stage, name) -> seen.add(stage));

        ScriptStageListeners.fireUnlocked("bronze", "Bronze Age");

        assertEquals(List.of("bronze"), seen);
    }

    @Test
    void clearingDropsListenersSoAReloadDoesNotStackThem() {
        List<String> seen = new ArrayList<>();
        ScriptStageListeners.onUnlocked((stage, name) -> seen.add(stage));
        ScriptStageListeners.clear();
        ScriptStageListeners.onUnlocked((stage, name) -> seen.add(stage));

        ScriptStageListeners.fireUnlocked("bronze", "Bronze Age");

        assertEquals(1, seen.size(), "a reloaded script must not be subscribed twice");
    }

    @Test
    void aThrowingListenerDoesNotStopTheOthers() {
        List<String> reported = new ArrayList<>();
        ScriptStageListeners.setErrorSink(reported::add);

        List<String> seen = new ArrayList<>();
        ScriptStageListeners.onUnlocked((stage, name) -> {
            throw new IllegalStateException("boom");
        });
        ScriptStageListeners.onUnlocked((stage, name) -> seen.add(stage));

        assertDoesNotThrow(() -> ScriptStageListeners.fireUnlocked("bronze", "Bronze Age"));
        assertEquals(List.of("bronze"), seen, "the second listener still has to run");
        assertEquals(1, reported.size(), "and the failure has to be reported, not swallowed");
        assertTrue(reported.get(0).contains("bronze"), reported.get(0));
    }

    @Test
    void aThrowingIndividualListenerIsReportedToo() {
        List<String> reported = new ArrayList<>();
        ScriptStageListeners.setErrorSink(reported::add);
        ScriptStageListeners.onIndividualUnlocked((stage, name, player) -> {
            throw new IllegalStateException("boom");
        });

        assertDoesNotThrow(() -> ScriptStageListeners.fireIndividualUnlocked(
                "tutorial", "Tutorial", UUID.randomUUID()));
        assertEquals(1, reported.size());
    }

    @Test
    void theFourChannelsAreSeparate() {
        List<String> seen = new ArrayList<>();
        ScriptStageListeners.onUnlocked((stage, name) -> seen.add("unlocked"));
        ScriptStageListeners.onLocked((stage, name) -> seen.add("locked"));

        ScriptStageListeners.fireLocked("bronze", "Bronze Age");

        assertEquals(List.of("locked"), seen);
    }

    @Test
    void anIndividualListenerGetsThePlayer() {
        UUID player = UUID.randomUUID();
        List<UUID> seen = new ArrayList<>();
        ScriptStageListeners.onIndividualLocked((stage, name, uuid) -> seen.add(uuid));

        ScriptStageListeners.fireIndividualLocked("tutorial", "Tutorial", player);

        assertEquals(List.of(player), seen);
    }
}
