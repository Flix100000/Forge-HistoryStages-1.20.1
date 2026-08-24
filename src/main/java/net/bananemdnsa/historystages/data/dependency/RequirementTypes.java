package net.bananemdnsa.historystages.data.dependency;


import net.bananemdnsa.historystages.api.dependency.Requirement;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bananemdnsa.historystages.api.stage.StageScope;
import org.jetbrains.annotations.Nullable;

/**
 * The kinds of requirement a dependency group can demand.
 *
 * <p>Iteration order is the order the checker evaluated them by hand, then addon requirements in
 * the order they registered. That order is what reaches the player — it decides the order entries
 * appear in the editor, in the graph and in a dependency result — so addons are appended rather
 * than interleaved.
 *
 * <p>Registration is legal in exactly one window — while {@link RegisterRequirementTypesEvent} is
 * being dispatched — and {@link #freeze()} closes it for good. Everything downstream may then
 * treat {@link #all()} as constant. An always-open registry would need invalidation everywhere it
 * is read, and would let a server and a client disagree about which requirement kinds exist.
 */
public final class RequirementTypes {

    // register()/freeze() can be reached from parallel mod construction, and byId()/all() can run
    // concurrently with either. `synchronized` on this monitor covers all of it; registration is a
    // rare, one-time, low-contention operation, so nothing finer is worth the care it would need.
    private static final Object LOCK = new Object();

    private static final Map<String, Requirement> BY_ID = new LinkedHashMap<>();
    // Captured once at bootstrap so builtIns() reflects exactly the eight the mod shipped with,
    // rather than guessing from the absence of a namespace at call time.
    private static final Set<String> BUILT_IN_IDS = new LinkedHashSet<>();
    private static boolean frozen = false;

    static {
        bootstrapBuiltIns();
    }

    private RequirementTypes() {}

    private static void bootstrapBuiltIns() {
        BY_ID.clear();
        BUILT_IN_IDS.clear();
        for (Requirement requirement : BuiltInRequirements.ALL) {
            BY_ID.put(requirement.id(), requirement);
            BUILT_IN_IDS.add(requirement.id());
        }
    }

    /** Every registered requirement, built-ins first, then addons in registration order. */
    public static List<Requirement> all() {
        synchronized (LOCK) {
            return List.copyOf(BY_ID.values());
        }
    }

    /**
     * The requirements that mean something at this scope, in registry order.
     *
     * <p>The one place the global/individual distinction is decided. It used to live as two
     * hardcoded arrays of tab keys in {@code DependencyEditorScreen}, which meant the editor hid
     * a kind while the checker went on evaluating it — a hand-written advancement on a global
     * stage was checked against whichever player happened to trigger it.
     */
    public static List<Requirement> forScope(StageScope scope) {
        synchronized (LOCK) {
            return BY_ID.values().stream()
                    .filter(requirement -> requirement.supportedScopes().contains(scope))
                    .toList();
        }
    }

    /**
     * Just the eight built-ins — {@link #all()} without whatever addons have registered. Exists
     * for the checks that only hold for the built-ins once addons can register their own.
     */
    public static List<Requirement> builtIns() {
        synchronized (LOCK) {
            return BY_ID.values().stream()
                    .filter(requirement -> BUILT_IN_IDS.contains(requirement.id()))
                    .toList();
        }
    }

    /** Every registered requirement id, in the same order as {@link #all()}. */
    public static List<String> ids() {
        synchronized (LOCK) {
            return List.copyOf(BY_ID.keySet());
        }
    }

    /** Null when nothing is registered under this id. */
    @Nullable
    public static Requirement byId(String id) {
        synchronized (LOCK) {
            return BY_ID.get(id);
        }
    }

    /**
     * Registers an addon requirement. Legal only before {@link #freeze()} — call it from a
     * {@link RegisterRequirementTypesEvent} listener.
     *
     * @throws IllegalStateException if the registry is already frozen
     * @throws IllegalArgumentException if the id is already taken, whether by a built-in or by
     *         another addon
     */
    public static void register(Requirement requirement) {
        synchronized (LOCK) {
            if (frozen) {
                throw new IllegalStateException(
                        "Requirement registration is closed; '" + requirement.id()
                                + "' tried to register after the freeze. Register from a "
                                + "RegisterRequirementTypesEvent listener instead.");
            }
            if (BY_ID.containsKey(requirement.id())) {
                throw new IllegalArgumentException(
                        "A requirement is already registered under id '" + requirement.id() + "'.");
            }
            BY_ID.put(requirement.id(), requirement);
        }
    }

    /**
     * Closes registration for good. Idempotent — calling it again is a no-op.
     *
     * <p>Deliberately silent: this class is exercised by unit tests, and the test runtime carries
     * no Minecraft or NeoForge, so anything logging through those would blow up on class load.
     * The caller does the reporting — see {@code HistoryStages}.
     */
    public static void freeze() {
        synchronized (LOCK) {
            frozen = true;
        }
    }

    /** The ids registered by other mods, in registration order. Empty before anyone registers. */
    public static List<String> addonIds() {
        synchronized (LOCK) {
            return BY_ID.keySet().stream()
                    .filter(id -> !BUILT_IN_IDS.contains(id))
                    .toList();
        }
    }

    /** Whether {@link #freeze()} has been called. */
    public static boolean isFrozen() {
        synchronized (LOCK) {
            return frozen;
        }
    }

    /**
     * Restores the registry to its pre-registration state: only the eight built-ins, unfrozen.
     *
     * <p>For tests only. A frozen static registry is otherwise untestable, and without a reset,
     * state registered by one test would leak into the next, making failures depend on execution
     * order. Production code must never call this.
     */
    public static void resetForTesting() {
        synchronized (LOCK) {
            bootstrapBuiltIns();
            frozen = false;
        }
    }
}
