package net.bananemdnsa.historystages.data.lock.category;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

/**
 * The lock categories the mod knows about.
 *
 * <p>Iteration order is the editor's tab order first, then addon categories in the order they
 * registered, so anything that walks the registry to build UI or reports comes out in an order
 * players already know, with addons appended rather than interleaved.
 *
 * <p>Registration is legal in exactly one window — while {@link RegisterLockCategoriesEvent} is
 * being dispatched — and {@link #freeze()} closes it for good. Everything downstream (editor
 * tabs, dual-phase detection, sync) may then treat {@link #all()} as constant. An always-open
 * registry would need invalidation everywhere it is read, and would let a server and a client
 * disagree about which categories exist — a desync source. See {@link RegisterLockCategoriesEvent}
 * for where the window opens and closes.
 */
public final class LockCategories {

    // register()/freeze() can be reached from parallel mod construction, and byId()/all() can run
    // concurrently with either. `synchronized` on this monitor covers all of it; a concurrent map
    // plus a volatile frozen flag would need the same amount of care for no real benefit here,
    // since registration is a rare, one-time, low-contention operation.
    private static final Object LOCK = new Object();

    private static final Map<String, LockCategory<?>> BY_ID = new LinkedHashMap<>();
    // Captured once at bootstrap so builtIns() reflects exactly the eleven ids the mod shipped
    // with, rather than guessing from the "historystages:" prefix at call time.
    private static final Set<String> BUILT_IN_IDS = new LinkedHashSet<>();
    private static boolean frozen = false;

    static {
        bootstrapBuiltIns();
    }

    private LockCategories() {}

    private static void bootstrapBuiltIns() {
        BUILT_IN_IDS.clear();
        for (LockCategory<?> category : BuiltInLockCategories.create()) {
            BY_ID.put(category.id(), category);
            BUILT_IN_IDS.add(category.id());
        }
    }

    /** Every registered category, in editor tab order followed by addon registration order. */
    public static List<LockCategory<?>> all() {
        synchronized (LOCK) {
            return List.copyOf(BY_ID.values());
        }
    }

    /**
     * Just the eleven built-in categories, in editor tab order — {@link #all()} without whatever
     * addons have registered. Exists for the parity check between the registry and the editor's
     * tabs, which only holds for the built-ins once addons can register their own.
     */
    public static List<LockCategory<?>> builtIns() {
        synchronized (LOCK) {
            return BY_ID.values().stream()
                    .filter(category -> BUILT_IN_IDS.contains(category.id()))
                    .toList();
        }
    }

    /** Every registered category id, in the same order as {@link #all()}. */
    public static List<String> ids() {
        synchronized (LOCK) {
            return List.copyOf(BY_ID.keySet());
        }
    }

    /** Null when nothing is registered under this id. */
    @Nullable
    public static LockCategory<?> byId(String id) {
        synchronized (LOCK) {
            return BY_ID.get(id);
        }
    }

    /**
     * Registers an addon category. Legal only before {@link #freeze()} — call it from a
     * {@link RegisterLockCategoriesEvent} listener.
     *
     * @throws IllegalStateException if the registry is already frozen
     * @throws IllegalArgumentException if {@code category}'s id is already taken, whether by a
     *         built-in or by another addon
     */
    public static void register(LockCategory<?> category) {
        synchronized (LOCK) {
            if (frozen) {
                throw new IllegalStateException(
                        "Lock category registration is closed; '" + category.id()
                                + "' tried to register after the freeze. Register from a "
                                + "RegisterLockCategoriesEvent listener instead.");
            }
            if (BY_ID.containsKey(category.id())) {
                throw new IllegalArgumentException(
                        "A lock category is already registered under id '" + category.id() + "'.");
            }
            BY_ID.put(category.id(), category);
        }
    }

    /**
     * Closes registration for good. Idempotent — calling it again is a no-op.
     *
     * <p>Deliberately silent: this class is exercised by unit tests, and the test runtime
     * classpath carries no Minecraft or NeoForge, so anything logging through those would blow
     * up on class load. The caller does the reporting — see {@code HistoryStages}.
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
     * Restores the registry to its pre-registration state: only the eleven built-ins, unfrozen.
     *
     * <p>For tests only. A frozen static registry is otherwise untestable, and without a reset,
     * state registered by one test would leak into the next, making failures depend on execution
     * order. Production code must never call this.
     */
    public static void resetForTesting() {
        synchronized (LOCK) {
            BY_ID.clear();
            bootstrapBuiltIns();
            frozen = false;
        }
    }
}
