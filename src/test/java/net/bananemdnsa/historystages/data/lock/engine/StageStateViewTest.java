package net.bananemdnsa.historystages.data.lock.engine;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageStateViewTest {

    @Test
    void ofSetReportsMembership() {
        StageStateView view = StageStateView.of(Set.of("bronze", "iron"));
        assertTrue(view.isUnlocked("bronze"));
        assertTrue(view.isUnlocked("iron"));
        assertFalse(view.isUnlocked("steel"));
    }

    @Test
    void ofNullBehavesAsEmpty() {
        StageStateView view = StageStateView.of(null);
        assertFalse(view.isUnlocked("bronze"));
    }

    @Test
    void noneUnlockedRejectsEverything() {
        assertFalse(StageStateView.NONE_UNLOCKED.isUnlocked("bronze"));
    }
}
