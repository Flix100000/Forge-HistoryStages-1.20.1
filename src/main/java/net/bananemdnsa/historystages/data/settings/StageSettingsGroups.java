package net.bananemdnsa.historystages.data.settings;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jetbrains.annotations.Nullable;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.engine.StageScope;

/**
 * The settings groups the mod knows about.
 *
 * <p>Iteration order is sorted by id rather than registration order, because registration order
 * depends on mod load order, which is not stable across instances — the editor needs an order
 * that is the same on every run. {@link #all()}, {@link #ids()} and {@link #forScope(StageScope)}
 * all share that ordering.
 *
 * <p>Registration is legal only until {@link #freeze()} closes it for good, mirroring {@link
 * net.bananemdnsa.historystages.data.lock.category.LockCategories} — see that class for the
 * rationale behind the freeze window.
 */
public final class StageSettingsGroups {

    // Same rationale as LockCategories: register()/freeze() can run during parallel mod
    // construction, and byId()/all() can run concurrently with either.
    private static final Object LOCK = new Object();

    private static final Map<String, StageSettingsGroup> BY_ID = new TreeMap<>();
    private static boolean frozen = false;

    private StageSettingsGroups() {}

    /** Every registered group, sorted by id. */
    public static List<StageSettingsGroup> all() {
        synchronized (LOCK) {
            return List.copyOf(BY_ID.values());
        }
    }

    /** Every registered group id, sorted. */
    public static List<String> ids() {
        synchronized (LOCK) {
            return List.copyOf(BY_ID.keySet());
        }
    }

    /** Null when nothing is registered under this id. */
    @Nullable
    public static StageSettingsGroup byId(String id) {
        synchronized (LOCK) {
            return BY_ID.get(id);
        }
    }

    /** The registered groups that support {@code scope}, sorted by id. */
    public static List<StageSettingsGroup> forScope(StageScope scope) {
        synchronized (LOCK) {
            return BY_ID.values().stream()
                    .filter(group -> group.supportedScopes().contains(scope))
                    .toList();
        }
    }

    /**
     * Resolves {@code groupId}'s values on {@code stage}, the Minecraft-free half of the lookup
     * — see {@link net.bananemdnsa.historystages.data.lock.category.CategoryLockResolver} for the
     * rationale behind taking the stage as an argument instead of reaching into
     * {@link net.bananemdnsa.historystages.data.StageManager}.
     *
     * <p>Asking is always safe: an unknown group id, a group that does not support {@code scope},
     * or a {@code null} stage all yield an empty {@link SettingsValues}, which returns every
     * field's own default rather than throwing.
     */
    public static SettingsValues valuesOf(String groupId, @Nullable StageEntry stage, StageScope scope) {
        StageSettingsGroup group = byId(groupId);
        if (group == null || !group.supportedScopes().contains(scope) || stage == null) {
            return SettingsValues.read(List.of(), null);
        }
        return group.load(stage, scope);
    }

    /**
     * Registers a settings group.
     *
     * @throws IllegalStateException if the registry is already frozen
     * @throws IllegalArgumentException if {@code group}'s id is already taken
     */
    public static void register(StageSettingsGroup group) {
        synchronized (LOCK) {
            if (frozen) {
                throw new IllegalStateException(
                        "Settings group registration is closed; '" + group.id()
                                + "' tried to register after the freeze.");
            }
            if (BY_ID.containsKey(group.id())) {
                throw new IllegalArgumentException(
                        "A settings group is already registered under id '" + group.id() + "'.");
            }
            BY_ID.put(group.id(), group);
        }
    }

    /**
     * Closes registration for good. Idempotent — calling it again is a no-op.
     *
     * <p>Deliberately silent: this class is exercised by unit tests, and the test runtime
     * classpath carries no Minecraft or NeoForge, so anything logging through those would blow
     * up on class load. The caller does the reporting.
     */
    public static void freeze() {
        synchronized (LOCK) {
            frozen = true;
        }
    }

    /** Whether {@link #freeze()} has been called. */
    public static boolean isFrozen() {
        synchronized (LOCK) {
            return frozen;
        }
    }

    /**
     * Restores the registry to its pre-registration state: empty, unfrozen.
     *
     * <p>For tests only. A frozen static registry is otherwise untestable, and without a reset,
     * state registered by one test would leak into the next, making failures depend on execution
     * order. Production code must never call this.
     */
    public static void resetForTesting() {
        synchronized (LOCK) {
            BY_ID.clear();
            frozen = false;
        }
    }
}
