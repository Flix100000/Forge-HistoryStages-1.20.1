package net.bananemdnsa.historystages.data.lock.engine;

import net.bananemdnsa.historystages.api.stage.StageStateView;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;

/**
 * Entry point for every lock question, and the single line a later phase changes to switch the
 * mod onto a BitSet engine.
 *
 * <p>Client-side {@link StageStateView} factories deliberately live in
 * {@code client.cache.ClientStageStates} instead of here: this class is reachable from
 * server-only code paths, and pulling client caches into it is how the crash fixed in
 * commit 0469f73 happened.
 */
public final class StageLocks {

    private static volatile StageLockEngine engine = new StringStageLockEngine();

    private StageLocks() {}

    public static StageLockEngine engine() {
        return engine;
    }

    public static void setEngine(StageLockEngine newEngine) {
        engine = newEngine == null ? new StringStageLockEngine() : newEngine;
    }

    /** Restores the default string-backed engine. Used by tests. */
    public static void resetEngine() {
        engine = new StringStageLockEngine();
    }

    /**
     * Tells the current engine that the stage maps changed — see
     * {@link StageLockEngine#stagesChanged()}.
     *
     * <p>Anything that writes to those maps has to come through here, including test helpers that
     * put stages in directly. A missed call leaves a stale index behind, and a stale index does
     * not throw: it quietly reports a staged item as irrelevant and unlocks it.
     */
    public static void stagesChanged() {
        DEFINITIONS_VERSION.incrementAndGet();
        engine.stagesChanged();
    }

    /**
     * Changes whenever the stage maps do. Never persisted, never sent.
     *
     * <p>The engine is told about a change; anything else that derives from the stage maps reads
     * this instead. Same reasoning as {@code StageData.cacheVersion}: a counter beside the data
     * cannot be forgotten the way a notification at each of a dozen write sites can, and this one
     * already has a single write site to sit in.
     */
    public static long definitionsVersion() {
        return DEFINITIONS_VERSION.get();
    }

    private static final java.util.concurrent.atomic.AtomicLong DEFINITIONS_VERSION =
            new java.util.concurrent.atomic.AtomicLong();

    /** The world's global unlocked set, server side. */
    public static StageStateView serverGlobal() {
        return StageData.SERVER_CACHE::contains;
    }

    /**
     * One player's individual unlocked set, server side — a <strong>snapshot</strong> taken now.
     *
     * <p>The player's set is resolved once here rather than on every question. That is worth
     * saying out loud because it used to be the other way round, and the difference is
     * measurable: a subject gated by twenty stages cost 364ns when each question repeated the
     * map lookup, against 99ns for the global view that does not. Under a mod-tiered pack, where
     * one item can be gated by every tier at once, that is the whole cost of the check.
     *
     * <p>The snapshot only holds because every caller builds a view at the point of use and
     * consumes it immediately. <strong>Do not store one across ticks:</strong>
     * {@code IndividualStageData} replaces a player's set wholesale on load and on resync, so a
     * held view would keep answering from the old one. A bitmask view is a snapshot by nature,
     * so this is also the semantics the engine is heading for.
     *
     * <p>{@link #serverGlobal()} stays live by contrast, and can: its set is a final field that
     * is only ever mutated in place.
     */
    public static StageStateView serverIndividual(UUID playerUuid) {
        Set<String> unlocked = IndividualStageData.SERVER_CACHE
                .getOrDefault(playerUuid, Collections.<String>emptySet());
        return unlocked::contains;
    }

    /** Snapshot variant for callers that already hold the player's set. */
    public static StageStateView serverIndividual(Set<String> playerStages) {
        return StageStateView.of(playerStages);
    }
}
