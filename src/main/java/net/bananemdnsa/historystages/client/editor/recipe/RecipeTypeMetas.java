package net.bananemdnsa.historystages.client.editor.recipe;

import net.bananemdnsa.historystages.api.editor.RecipeTypeMeta;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every recipe type the editor knows how to draw, vanilla first, then addons in registration
 * order.
 *
 * <p>Registration is legal in exactly one window — while {@code RegisterRecipeTypeMetaEvent} is
 * being dispatched — and {@link #freeze()} closes it for good, the same shape as
 * {@code RequirementTypes}. Everything downstream may then treat the table as constant.
 *
 * <p>What this replaces: {@code getWorkstationForType}, {@code getRecipeTypeName} and
 * {@code getRecipeTypeAccentColor} in {@code StageDetailScreen}, plus a second copy of the first
 * and a second, disagreeing colour table in {@code SearchableRecipeList}.
 */
public final class RecipeTypeMetas {

    // register()/freeze() run during mod construction and get()/all() run on the render thread.
    // Registration is rare and one-time, so one monitor over everything is cheap enough.
    private static final Object LOCK = new Object();

    private static final Map<String, RecipeTypeMeta> BY_ID = new LinkedHashMap<>();
    private static final Set<String> BUILT_IN_IDS = new LinkedHashSet<>();
    private static boolean frozen = false;

    /**
     * The seven types vanilla ships. Colours are the {@code StageDetailScreen} set, which is the
     * one the recipe preview popup has been showing; the picker's second, slightly different set
     * is dropped rather than merged.
     */
    private static final List<RecipeTypeMeta> BUILT_INS = List.of(
            new RecipeTypeMeta("minecraft:crafting", "minecraft:crafting_table", 0xFFFFCC00,
                    "editor.historystages.recipe_type.crafting"),
            new RecipeTypeMeta("minecraft:smelting", "minecraft:furnace", 0xFFFF8800,
                    "editor.historystages.recipe_type.smelting"),
            new RecipeTypeMeta("minecraft:blasting", "minecraft:blast_furnace", 0xFFFF4400,
                    "editor.historystages.recipe_type.blasting"),
            new RecipeTypeMeta("minecraft:smoking", "minecraft:smoker", 0xFF996633,
                    "editor.historystages.recipe_type.smoking"),
            new RecipeTypeMeta("minecraft:campfire_cooking", "minecraft:campfire", 0xFFFF6600,
                    "editor.historystages.recipe_type.campfire_cooking"),
            new RecipeTypeMeta("minecraft:stonecutting", "minecraft:stonecutter", 0xFF888888,
                    "editor.historystages.recipe_type.stonecutting"),
            new RecipeTypeMeta("minecraft:smithing", "minecraft:smithing_table", 0xFF6688AA,
                    "editor.historystages.recipe_type.smithing"));

    /** Accent for a type nobody described: neutral, so it reads as "unknown" rather than as a type. */
    private static final int UNKNOWN_ACCENT = 0xFF55CC55;

    static {
        bootstrapBuiltIns();
    }

    private RecipeTypeMetas() {
    }

    private static void bootstrapBuiltIns() {
        BY_ID.clear();
        BUILT_IN_IDS.clear();
        for (RecipeTypeMeta meta : BUILT_INS) {
            BY_ID.put(meta.typeId(), meta);
            BUILT_IN_IDS.add(meta.typeId());
        }
    }

    /**
     * What to draw for {@code typeId}. Never null: an unknown type gets a placeholder naming
     * itself, because the caller is mid-frame and has nothing useful to do with a null.
     */
    public static RecipeTypeMeta get(String typeId) {
        String id = typeId == null ? "" : typeId;
        synchronized (LOCK) {
            RecipeTypeMeta known = BY_ID.get(id);
            if (known != null) return known;
        }
        return new RecipeTypeMeta(id, "", UNKNOWN_ACCENT, "");
    }

    /** Every registered type, built-ins first, then addons in registration order. */
    public static List<RecipeTypeMeta> all() {
        synchronized (LOCK) {
            return List.copyOf(BY_ID.values());
        }
    }

    /** The ids registered by other mods, in registration order. */
    public static List<String> addonIds() {
        synchronized (LOCK) {
            return BY_ID.keySet().stream().filter(id -> !BUILT_IN_IDS.contains(id)).toList();
        }
    }

    /**
     * Registers an addon's recipe type. Legal only before {@link #freeze()} — call it from a
     * {@code RegisterRecipeTypeMetaEvent} listener.
     *
     * @throws IllegalStateException    if the registry is already frozen
     * @throws IllegalArgumentException if the id is already taken
     */
    public static void register(RecipeTypeMeta meta) {
        synchronized (LOCK) {
            if (frozen) {
                throw new IllegalStateException(
                        "Recipe type metadata registration is closed; '" + meta.typeId()
                                + "' tried to register after the freeze. Register from a "
                                + "RegisterRecipeTypeMetaEvent listener instead.");
            }
            if (BY_ID.containsKey(meta.typeId())) {
                throw new IllegalArgumentException(
                        "Recipe type metadata is already registered under id '" + meta.typeId()
                                + "'.");
            }
            BY_ID.put(meta.typeId(), meta);
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
     * Restores the registry to built-ins only, unfrozen.
     *
     * <p>For tests only. Without it, a type registered by one test leaks into the next and
     * failures start depending on execution order — which has already bitten the GameTests here.
     * Production code must never call this.
     */
    public static void resetForTesting() {
        synchronized (LOCK) {
            bootstrapBuiltIns();
            frozen = false;
        }
    }
}
