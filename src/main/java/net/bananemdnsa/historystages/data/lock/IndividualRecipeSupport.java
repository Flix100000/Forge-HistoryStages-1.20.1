package net.bananemdnsa.historystages.data.lock;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which recipe types an individual stage can actually gate.
 *
 * <p>The gate hangs on the <em>menu</em>: a station only knows who is crafting if a player is
 * standing at it. Furnaces, hoppers and autocrafters resolve recipes with nobody there, so one of
 * their recipes on an individual stage would sit in the file doing nothing at all.
 *
 * <p><strong>The type is a stand-in, not a guarantee.</strong> A mod machine with its own menu
 * that uses {@code minecraft:crafting} internally does not pass through our hooks. It is still the
 * best filter available from a recipe id, and it is documented as "supported as far as we know".
 *
 * <p><strong>Registering here is a promise.</strong> Since 6.0.0 other mods may add their own
 * types through {@code RegisterIndividualRecipeSupportEvent}. Whoever does is asserting that
 * their station resolves recipes with a player in front of it and that HistoryStages' hooks see
 * that player. An id registered without such a station makes the picker offer a per-player lock
 * that does not exist: the entry lands in the stage file and silently does nothing.
 *
 * <p>One registry, two readers: the editor's recipe picker and the load-time audit. Ids rather
 * than {@code RecipeType} constants so the rule stays testable — the test classpath carries no
 * Minecraft.
 */
public final class IndividualRecipeSupport {

    // Registration happens during mod construction; supports() is read from the render thread and
    // from the datapack-reload audit. One monitor covers a rare write and frequent cheap reads.
    private static final Object LOCK = new Object();

    /**
     * Vanilla ships exactly seven recipe types. These three resolve at a station with a player in
     * front of it: the crafting table and the 2x2 inventory grid, the stonecutter, the smithing
     * table. The four cooking types run in a block entity with nobody there.
     *
     * <p>Adding an id here without adding the matching menu hook makes the picker promise a gate
     * that does not exist.
     */
    private static final Set<String> BUILT_IN_IDS =
            Set.of("minecraft:crafting", "minecraft:stonecutting", "minecraft:smithing");

    private static final Set<String> SUPPORTED = new LinkedHashSet<>();
    private static boolean frozen = false;

    static {
        bootstrapBuiltIns();
    }

    private IndividualRecipeSupport() {}

    private static void bootstrapBuiltIns() {
        SUPPORTED.clear();
        SUPPORTED.addAll(BUILT_IN_IDS);
    }

    /** @param recipeTypeId the registry id of a recipe type, e.g. {@code minecraft:crafting} */
    public static boolean supports(String recipeTypeId) {
        if (recipeTypeId == null) return false;
        synchronized (LOCK) {
            return SUPPORTED.contains(recipeTypeId);
        }
    }

    /** Every supported type id, built-ins first, then addons in registration order. */
    public static List<String> supportedTypeIds() {
        synchronized (LOCK) {
            return List.copyOf(SUPPORTED);
        }
    }

    /** Just the three the mod ships with. */
    public static List<String> builtInIds() {
        return List.copyOf(BUILT_IN_IDS);
    }

    /** The ids registered by other mods, in registration order. */
    public static List<String> addonIds() {
        synchronized (LOCK) {
            return SUPPORTED.stream().filter(id -> !BUILT_IN_IDS.contains(id)).toList();
        }
    }

    /**
     * Declares that this recipe type resolves at a station that knows who is using it. Legal only
     * before {@link #freeze()} — call it from a {@code RegisterIndividualRecipeSupportEvent}
     * listener.
     *
     * <p>Registering an id already present is a no-op, not an error: two addons can plausibly
     * both vouch for the same third-party type, and neither should bring the game down for it.
     *
     * @throws IllegalStateException    if registration is already closed
     * @throws IllegalArgumentException if the id is null or blank
     */
    public static void register(String recipeTypeId) {
        if (recipeTypeId == null || recipeTypeId.isBlank()) {
            throw new IllegalArgumentException(
                    "A recipe type id is required; got " + (recipeTypeId == null ? "null" : "blank"));
        }
        synchronized (LOCK) {
            if (frozen) {
                throw new IllegalStateException(
                        "Individual recipe support is closed; '" + recipeTypeId
                                + "' tried to register after the freeze. Register from a "
                                + "RegisterIndividualRecipeSupportEvent listener instead.");
            }
            SUPPORTED.add(recipeTypeId);
        }
    }

    /** Closes registration for good. Idempotent. */
    public static void freeze() {
        synchronized (LOCK) {
            frozen = true;
        }
    }

    public static boolean isFrozen() {
        synchronized (LOCK) {
            return frozen;
        }
    }

    /**
     * Restores the registry to the built-ins, unfrozen.
     *
     * <p>For tests only. Production code must never call this.
     */
    public static void resetForTesting() {
        synchronized (LOCK) {
            bootstrapBuiltIns();
            frozen = false;
        }
    }
}
