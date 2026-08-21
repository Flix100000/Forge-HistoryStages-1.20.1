package net.bananemdnsa.historystages.data;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.bananemdnsa.historystages.data.lock.engine.StageScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageFileGuardTest {

    private static final UUID ALICE = UUID.nameUUIDFromBytes("alice".getBytes(StandardCharsets.UTF_8));
    private static final UUID BOB = UUID.nameUUIDFromBytes("bob".getBytes(StandardCharsets.UTF_8));

    private static final byte[] LOADED = "{a}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] EDITED = "{a,b}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] EDITED_AGAIN = "{a,b,c}".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    @AfterEach
    void reset() {
        StageFileGuard.resetForTesting();
    }

    @Test
    void aBrandNewStageWithNoFileMayBeWritten() {
        assertTrue(StageFileGuard.mayWrite(ALICE, "bronze", StageScope.GLOBAL, null));
    }

    @Test
    void anUnchangedFileMayBeWritten() {
        StageFileGuard.recordLoaded("bronze", StageScope.GLOBAL, LOADED);

        assertTrue(StageFileGuard.mayWrite(ALICE, "bronze", StageScope.GLOBAL, LOADED));
    }

    @Test
    void aChangedFileIsRefused() {
        StageFileGuard.recordLoaded("bronze", StageScope.GLOBAL, LOADED);

        assertFalse(StageFileGuard.mayWrite(ALICE, "bronze", StageScope.GLOBAL, EDITED));
    }

    /**
     * A file the server never loaded is exactly a file somebody created by hand while it was
     * running. Missing information is not permission.
     */
    @Test
    void aFileWithNoRecordedFingerprintIsRefused() {
        assertFalse(StageFileGuard.mayWrite(ALICE, "bronze", StageScope.GLOBAL, EDITED));
    }

    @Test
    void aSecondAttemptOnTheSameStateIsAllowed() {
        StageFileGuard.recordLoaded("bronze", StageScope.GLOBAL, LOADED);
        assertFalse(StageFileGuard.mayWrite(ALICE, "bronze", StageScope.GLOBAL, EDITED));

        assertTrue(StageFileGuard.mayWrite(ALICE, "bronze", StageScope.GLOBAL, EDITED),
                "the warning named this exact file state, so confirming it must go through");
    }

    @Test
    void aSecondAttemptOnAFurtherChangedStateIsRefusedAgain() {
        StageFileGuard.recordLoaded("bronze", StageScope.GLOBAL, LOADED);
        assertFalse(StageFileGuard.mayWrite(ALICE, "bronze", StageScope.GLOBAL, EDITED));

        assertFalse(StageFileGuard.mayWrite(ALICE, "bronze", StageScope.GLOBAL, EDITED_AGAIN),
                "consent covered the state that was shown, not the stage in general");
    }

    /**
     * Covers the realistic sequence: refuse, confirm, write, then the caller refreshes the
     * recorded fingerprint. Because that refresh makes {@code EDITED_AGAIN} mismatch both
     * {@code RECORDED} and the stale consent, this test cannot by itself tell whether
     * {@link StageFileGuard#consume} actually did anything — see
     * {@link #consumeAloneTurnsAnAllowedRetryBackIntoARefusal()} below for the test that isolates
     * that.
     */
    @Test
    void consentSurvivesOnlyUntilTheWriteHappens() {
        StageFileGuard.recordLoaded("bronze", StageScope.GLOBAL, LOADED);
        assertFalse(StageFileGuard.mayWrite(ALICE, "bronze", StageScope.GLOBAL, EDITED));
        assertTrue(StageFileGuard.mayWrite(ALICE, "bronze", StageScope.GLOBAL, EDITED));

        StageFileGuard.consume(ALICE, "bronze", StageScope.GLOBAL);
        StageFileGuard.recordLoaded("bronze", StageScope.GLOBAL, EDITED);

        assertFalse(StageFileGuard.mayWrite(ALICE, "bronze", StageScope.GLOBAL, EDITED_AGAIN),
                "a later hand edit must warn again rather than ride the spent consent");
    }

    /**
     * Isolates {@link StageFileGuard#consume} by deliberately not refreshing the recorded
     * fingerprint afterwards, which the real caller always does. Without that refresh the only
     * thing that can turn the allowed second attempt back into a refusal is the consent being
     * gone - so unlike the realistic sequence above, this fails if consume does nothing.
     */
    @Test
    void consumeAloneTurnsAnAllowedRetryBackIntoARefusal() {
        StageFileGuard.recordLoaded("bronze", StageScope.GLOBAL, LOADED);
        assertFalse(StageFileGuard.mayWrite(ALICE, "bronze", StageScope.GLOBAL, EDITED));
        assertTrue(StageFileGuard.mayWrite(ALICE, "bronze", StageScope.GLOBAL, EDITED));

        StageFileGuard.consume(ALICE, "bronze", StageScope.GLOBAL);

        assertFalse(StageFileGuard.mayWrite(ALICE, "bronze", StageScope.GLOBAL, EDITED),
                "consume did not clear the consent: the same state was waved through twice");
    }

    @Test
    void oneRefusalDoesNotLetAnotherPlayerWrite() {
        StageFileGuard.recordLoaded("bronze", StageScope.GLOBAL, LOADED);
        assertFalse(StageFileGuard.mayWrite(ALICE, "bronze", StageScope.GLOBAL, EDITED));

        assertFalse(StageFileGuard.mayWrite(BOB, "bronze", StageScope.GLOBAL, EDITED),
                "Bob was never shown the warning");
    }

    @Test
    void consentForOneStageDoesNotCoverAnother() {
        StageFileGuard.recordLoaded("bronze", StageScope.GLOBAL, LOADED);
        StageFileGuard.recordLoaded("iron", StageScope.GLOBAL, LOADED);
        assertFalse(StageFileGuard.mayWrite(ALICE, "bronze", StageScope.GLOBAL, EDITED));

        assertFalse(StageFileGuard.mayWrite(ALICE, "iron", StageScope.GLOBAL, EDITED));
    }

    @Test
    void consentForAGlobalStageDoesNotCoverAnIndividualOneOfTheSameId() {
        StageFileGuard.recordLoaded("bronze", StageScope.GLOBAL, LOADED);
        StageFileGuard.recordLoaded("bronze", StageScope.INDIVIDUAL, LOADED);
        assertFalse(StageFileGuard.mayWrite(ALICE, "bronze", StageScope.GLOBAL, EDITED));

        assertFalse(StageFileGuard.mayWrite(ALICE, "bronze", StageScope.INDIVIDUAL, EDITED));
    }

    @Test
    void theTwoScopesTrackFingerprintsSeparately() {
        StageFileGuard.recordLoaded("bronze", StageScope.GLOBAL, LOADED);
        StageFileGuard.recordLoaded("bronze", StageScope.INDIVIDUAL, EDITED);

        assertTrue(StageFileGuard.mayWrite(ALICE, "bronze", StageScope.GLOBAL, LOADED));
        assertTrue(StageFileGuard.mayWrite(ALICE, "bronze", StageScope.INDIVIDUAL, EDITED));
    }
}
