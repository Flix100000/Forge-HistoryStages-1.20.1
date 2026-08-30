package net.bananemdnsa.historystages.compat.script;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Stage-change listeners registered from a script, kept outside any script engine so that both
 * the registry and the clearing rule live in one Minecraft-free place.
 *
 * <p>Cleared on every script reload. Without that, a CraftTweaker script that subscribes at load
 * time would be subscribed twice after the first {@code /reload} and n+1 times after n of them —
 * the kind of bug that only shows up on a long-running server.
 *
 * <p>KubeJS does not use this: its {@code EventGroup} keeps its own handlers and drops them on
 * reload. CraftTweaker has no such registry for a third-party mod's events, so this is it.
 *
 * <p>Failures go to a settable sink rather than straight to {@code DebugLogger}, which drags in
 * NeoForge and the config and would make this class unloadable in a unit test. The bridge that
 * uses the registry points the sink at the real logger; the default writes to stderr so a
 * misconfigured setup is still noisy rather than silent.
 */
public final class ScriptStageListeners {

    private ScriptStageListeners() {}

    /** A global stage changed. */
    @FunctionalInterface
    public interface StageListener {
        void accept(String stageId, String displayName);
    }

    /** An individual stage changed for one player. */
    @FunctionalInterface
    public interface IndividualStageListener {
        void accept(String stageId, String displayName, UUID playerUuid);
    }

    private static final Consumer<String> DEFAULT_SINK = System.err::println;

    private static final List<StageListener> UNLOCKED = new CopyOnWriteArrayList<>();
    private static final List<StageListener> LOCKED = new CopyOnWriteArrayList<>();
    private static final List<IndividualStageListener> INDIVIDUAL_UNLOCKED = new CopyOnWriteArrayList<>();
    private static final List<IndividualStageListener> INDIVIDUAL_LOCKED = new CopyOnWriteArrayList<>();

    private static volatile Consumer<String> errorSink = DEFAULT_SINK;

    public static void onUnlocked(StageListener listener) {
        UNLOCKED.add(listener);
    }

    public static void onLocked(StageListener listener) {
        LOCKED.add(listener);
    }

    public static void onIndividualUnlocked(IndividualStageListener listener) {
        INDIVIDUAL_UNLOCKED.add(listener);
    }

    public static void onIndividualLocked(IndividualStageListener listener) {
        INDIVIDUAL_LOCKED.add(listener);
    }

    public static void fireUnlocked(String stageId, String displayName) {
        fire(UNLOCKED, stageId, displayName);
    }

    public static void fireLocked(String stageId, String displayName) {
        fire(LOCKED, stageId, displayName);
    }

    public static void fireIndividualUnlocked(String stageId, String displayName, UUID player) {
        fireIndividual(INDIVIDUAL_UNLOCKED, stageId, displayName, player);
    }

    public static void fireIndividualLocked(String stageId, String displayName, UUID player) {
        fireIndividual(INDIVIDUAL_LOCKED, stageId, displayName, player);
    }

    /** Called when scripts are reloaded, before they register again. */
    public static void clear() {
        UNLOCKED.clear();
        LOCKED.clear();
        INDIVIDUAL_UNLOCKED.clear();
        INDIVIDUAL_LOCKED.clear();
    }

    /** Points listener failures at the mod's logger; called by the bridge that owns them. */
    public static void setErrorSink(Consumer<String> sink) {
        errorSink = sink == null ? DEFAULT_SINK : sink;
    }

    public static void resetErrorSink() {
        errorSink = DEFAULT_SINK;
    }

    private static void fire(List<StageListener> listeners, String stageId, String displayName) {
        for (StageListener listener : listeners) {
            try {
                listener.accept(stageId, displayName);
            } catch (RuntimeException e) {
                report(stageId, e);
            }
        }
    }

    private static void fireIndividual(List<IndividualStageListener> listeners, String stageId,
                                       String displayName, UUID player) {
        for (IndividualStageListener listener : listeners) {
            try {
                listener.accept(stageId, displayName, player);
            } catch (RuntimeException e) {
                report(stageId, e);
            }
        }
    }

    /**
     * One bad script must not stop the others, and must not take the unlock with it: this runs
     * from inside {@code StageStates}, after the stage has already been granted, so throwing on
     * would leave a stage that is unlocked in the data and half-announced to the world.
     */
    private static void report(String stageId, RuntimeException e) {
        errorSink.accept("HistoryStages: a script listener threw on stage '" + stageId + "': " + e);
    }
}
