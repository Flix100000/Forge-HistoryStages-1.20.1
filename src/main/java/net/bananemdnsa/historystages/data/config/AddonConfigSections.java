package net.bananemdnsa.historystages.data.config;

import net.bananemdnsa.historystages.api.config.ConfigSide;

import net.bananemdnsa.historystages.api.config.AddonConfigField;

import net.bananemdnsa.historystages.api.config.AddonConfigSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

/**
 * The addon config sections the mod knows about.
 *
 * <p>Iteration order is sorted by id rather than registration order, because registration order
 * depends on mod load order, which is not stable across instances — the screen needs an order
 * that is the same on every run. {@link #all()} and {@link #forSide(ConfigSide)} both share that
 * ordering.
 *
 * <p>Registration is legal only until {@link #freeze()} closes it for good, mirroring {@link
 * net.bananemdnsa.historystages.data.lock.category.LockCategories} and {@link
 * net.bananemdnsa.historystages.data.settings.StageSettingsGroups} — see the former for the
 * rationale behind the freeze window and the split between {@code IllegalArgumentException} for a
 * duplicate id and {@code IllegalStateException} for registering after the freeze.
 */
public final class AddonConfigSections {

    // Same rationale as LockCategories: register()/freeze() can run during parallel mod
    // construction, and byId()/all() can run concurrently with either.
    private static final Object LOCK = new Object();

    private static final Map<String, AddonConfigSection> BY_ID = new TreeMap<>();
    private static boolean frozen = false;

    private AddonConfigSections() {}

    /** Every registered section, sorted by id. */
    public static List<AddonConfigSection> all() {
        synchronized (LOCK) {
            return List.copyOf(BY_ID.values());
        }
    }

    /** Null when nothing is registered under this id. */
    @Nullable
    public static AddonConfigSection byId(String id) {
        synchronized (LOCK) {
            return BY_ID.get(id);
        }
    }

    /** The registered sections on {@code side}, sorted by id. */
    public static List<AddonConfigSection> forSide(ConfigSide side) {
        synchronized (LOCK) {
            return BY_ID.values().stream()
                    .filter(section -> section.side() == side)
                    .toList();
        }
    }

    /**
     * Registers a config section.
     *
     * @throws IllegalStateException if the registry is already frozen
     * @throws IllegalArgumentException if {@code section}'s id is already taken
     */
    public static void register(AddonConfigSection section) {
        synchronized (LOCK) {
            if (frozen) {
                throw new IllegalStateException(
                        "Config section registration is closed; '" + section.id()
                                + "' tried to register after the freeze.");
            }
            if (BY_ID.containsKey(section.id())) {
                throw new IllegalArgumentException(
                        "A config section is already registered under id '" + section.id() + "'.");
            }
            BY_ID.put(section.id(), section);
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

    /** One publishable value: the wire key it travels under, and the addon's read/write pair. */
    public record CommonEntry(String wireKey, Supplier<String> read, Consumer<String> write) {}

    /**
     * The wire key a field travels under. The single place this is built.
     *
     * <p>The config screen and the config packets both need it, and if their two ideas
     * of the name ever diverge the value is collected under one and applied under the other — the
     * row saves, nothing errors, and the setting simply never changes.
     */
    public static String wireKey(AddonConfigSection section, AddonConfigField field) {
        return section.id() + "." + field.key();
    }

    /**
     * Every COMMON section's fields, as the entries that would be published. The wire key is
     * minted here and nowhere else: the publisher and the config screen must agree on it exactly,
     * and a value collected under one name and applied under another saves without complaint and
     * changes nothing.
     */
    public static List<CommonEntry> commonEntries() {
        synchronized (LOCK) {
            List<CommonEntry> entries = new ArrayList<>();
            for (AddonConfigSection section : BY_ID.values()) {
                if (section.side() != ConfigSide.COMMON) continue;
                for (AddonConfigField field : section.fields()) {
                    entries.add(new CommonEntry(wireKey(section, field), field.read(), field.write()));
                }
            }
            return List.copyOf(entries);
        }
    }
}
